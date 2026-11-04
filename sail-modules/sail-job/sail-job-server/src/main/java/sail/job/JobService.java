package sail.job;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.BusinessException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class JobService {
    private final JobStore store;
    private final HandlerRegistry handlers;

    @Value("${sail.job.enabled:true}")
    private boolean enabled;

    void available() {
        if (!enabled) throw new BusinessException("任务调度已由运行配置关闭");
    }

    public List<Long> preview(JobModels.Input input) {
        validate(input);
        return JobSchedules.preview(input);
    }

    private String validate(JobModels.Input input) {
        available();
        var handler = handlers.get(input.handler());
        if (input.maxRetries() > 0 && !handler.retryable())
            throw new BusinessException("该处理器未声明幂等可重试，不能启用自动重试");
        JobSchedules.preview(input);
        return handler.validate(input.parameters());
    }

    @Transactional
    public long save(Long id, JobModels.Input input) {
        String parameters = validate(input);
        long now = System.currentTimeMillis();
        if (id == null) {
            return store.insert(
                    "INSERT INTO"
                        + " sys_job(name,group_name,handler,parameters,schedule_type,cron,interval_seconds,zone_id,misfire_policy,timeout_seconds,max_retries,retry_delay_seconds,retention_days,created_at,updated_at)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    input.name().strip(),
                    input.groupName().strip(),
                    input.handler(),
                    parameters,
                    input.scheduleType(),
                    input.cron(),
                    input.intervalSeconds(),
                    input.zoneId(),
                    input.misfirePolicy(),
                    input.timeoutSeconds(),
                    input.maxRetries(),
                    input.retryDelaySeconds(),
                    input.retentionDays(),
                    now,
                    now);
        }
        var job = store.job(id, true);
        version(job, input.version());
        if ("ARCHIVED".equals(job.status())) throw new BusinessException("归档任务不能修改，请新建任务");
        store.db()
                .update(
                        "UPDATE sys_job SET"
                            + " name=?,group_name=?,handler=?,parameters=?,schedule_type=?,cron=?,interval_seconds=?,zone_id=?,misfire_policy=?,timeout_seconds=?,max_retries=?,retry_delay_seconds=?,retention_days=?,version=version+1,sync_status='PENDING',updated_at=?"
                            + " WHERE id=?",
                        input.name().strip(),
                        input.groupName().strip(),
                        input.handler(),
                        parameters,
                        input.scheduleType(),
                        input.cron(),
                        input.intervalSeconds(),
                        input.zoneId(),
                        input.misfirePolicy(),
                        input.timeoutSeconds(),
                        input.maxRetries(),
                        input.retryDelaySeconds(),
                        input.retentionDays(),
                        now,
                        id);
        cancelQueued(job, "任务配置已更新，旧排队执行取消");
        return id;
    }

    @Transactional
    public void control(long id, JobModels.Control input) {
        available();
        var job = store.job(id, true);
        version(job, input.version());
        if ("ARCHIVED".equals(job.status())) throw new BusinessException("归档任务不能重新启用");
        if ("ARCHIVED".equals(input.status()) && job.activeRunId() != null) {
            var run = store.run(job.activeRunId());
            if (!"QUEUED".equals(run.status())) throw new BusinessException("任务仍在运行或结果未知，不能归档");
        }
        store.db()
                .update(
                        "UPDATE sys_job SET"
                            + " status=?,version=version+1,sync_status='PENDING',updated_at=? WHERE"
                            + " id=?",
                        input.status(),
                        System.currentTimeMillis(),
                        id);
        cancelQueued(job, "任务状态已变更，旧排队执行取消");
    }

    private void cancelQueued(JobModels.Definition job, String summary) {
        if (job.activeRunId() == null) return;
        int count =
                store.db()
                        .update(
                                "UPDATE sys_job_execution SET"
                                    + " status='SKIPPED',finished_at=?,summary=? WHERE id=? AND"
                                    + " status='QUEUED'",
                                System.currentTimeMillis(),
                                summary,
                                job.activeRunId());
        if (count == 1)
            store.db()
                    .update(
                            "UPDATE sys_job SET active_run_id=NULL WHERE id=? AND active_run_id=?",
                            job.id(),
                            job.activeRunId());
    }

    static void version(JobModels.Definition job, long version) {
        if (job.version() != version) throw new BusinessException(409, "任务已被其他操作更新，请刷新后重试");
    }

    @Transactional
    public long trigger(long id, long version, Long parent, long actor, String actorName) {
        available();
        var job = store.job(id, true);
        version(job, version);
        if (!"ENABLED".equals(job.status()) || !"SYNCED".equals(job.syncStatus()))
            throw new BusinessException("请先启用任务并等待调度同步成功");
        if (job.activeRunId() != null) throw new BusinessException(409, "已有排队、运行或结果未知的执行，请勿重复触发");
        int attempt = 0;
        if (parent != null) {
            var previous = store.run(parent);
            if (previous.jobId() != id
                    || previous.jobVersion() != version
                    || !Set.of("FAILED", "PARTIAL").contains(previous.status())
                    || previous.finishedAt() == null)
                throw new BusinessException("只能重试当前配置版本中已结束的失败或部分失败执行");
            if (!handlers.get(job.handler()).retryable())
                throw new BusinessException("该处理器未声明幂等，不能重试");
            attempt = previous.attempt() + 1;
        }
        long runId =
                store.newRun(
                        job,
                        parent == null ? "MANUAL" : "RETRY",
                        actor,
                        actorName,
                        attempt,
                        parent,
                        System.currentTimeMillis(),
                        "QUEUED");
        store.db().update("UPDATE sys_job SET active_run_id=? WHERE id=?", runId, id);
        return runId; // 排队事实先提交，维护器可靠派发；进程中断后仍可恢复。
    }

    @Transactional
    public void resolve(long runId, String reason) {
        available();
        var initial = store.run(runId);
        var job = store.job(initial.jobId(), true);
        var run = store.run(runId);
        if (!"UNKNOWN".equals(run.status()) || !Objects.equals(job.activeRunId(), runId))
            throw new BusinessException("仅可处理当前结果未知的执行");
        store.db()
                .update(
                        "UPDATE sys_job_execution SET finished_at=?,resolution=? WHERE id=?",
                        System.currentTimeMillis(),
                        reason.strip(),
                        runId);
        store.db().update("UPDATE sys_job SET active_run_id=NULL WHERE id=?", job.id());
    }
}
