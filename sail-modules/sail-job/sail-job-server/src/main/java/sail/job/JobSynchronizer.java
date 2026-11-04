package sail.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.quartz.*;
import org.springframework.stereotype.Service;

import java.util.*;

/** 配置事实和执行 outbox 均在数据库；每次同步持有同一任务行锁，避免旧版本覆盖新版本。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobSynchronizer {
    private final JobStore store;
    private final Scheduler scheduler;
    private final JobTransactions tx;
    private final HandlerRegistry handlers;

    public void sync(long id) {
        tx.executeWithoutResult(
                status -> {
                    var job = store.job(id, true);
                    try {
                        if ("ARCHIVED".equals(job.status())) {
                            scheduler.deleteJob(JobSchedules.key(id));
                            synced(job, null);
                            return;
                        }
                        handlers.get(job.handler());
                        if (!scheduler.checkExists(JobSchedules.key(id)))
                            scheduler.addJob(
                                    JobBuilder.newJob(ManagedJob.class)
                                            .withIdentity(JobSchedules.key(id))
                                            .usingJobData("jobId", Long.toString(id))
                                            .storeDurably()
                                            .build(),
                                    false);
                        Trigger existing = scheduler.getTrigger(JobSchedules.trigger(id));
                        if (!"ENABLED".equals(job.status())) {
                            scheduler.unscheduleJob(JobSchedules.trigger(id));
                            synced(job, null);
                            return;
                        }
                        if (existing == null
                                || !Long.toString(job.version())
                                        .equals(existing.getJobDataMap().getString("version"))) {
                            Trigger next = JobSchedules.build(job);
                            if (existing == null) scheduler.scheduleJob(next);
                            else scheduler.rescheduleJob(JobSchedules.trigger(id), next);
                            existing = scheduler.getTrigger(JobSchedules.trigger(id));
                        }
                        synced(job, existing == null ? null : existing.getNextFireTime());
                    } catch (Exception e) {
                        // 仅保存异常类型；数据库连接字符串、参数和凭据不进入公开错误摘要。
                        store.db()
                                .update(
                                        "UPDATE sys_job SET sync_status='ERROR',sync_error=? WHERE"
                                            + " id=?",
                                        "调度同步失败：" + e.getClass().getSimpleName(),
                                        id);
                        log.warn("任务调度同步失败 jobId={} type={}", id, e.getClass().getSimpleName());
                        log.debug("调度同步诊断 jobId=" + id, e);
                    }
                });
    }

    private void synced(JobModels.Definition job, Date next) {
        store.db()
                .update(
                        "UPDATE sys_job SET"
                            + " synced_version=?,sync_status='SYNCED',sync_error=NULL,next_fire_at=?"
                            + " WHERE id=?",
                        job.version(),
                        next == null ? null : next.getTime(),
                        job.id());
    }

    public void dispatch(long runId) {
        var initial = store.run(runId);
        tx.executeWithoutResult(
                status -> {
                    var job = store.job(initial.jobId(), true);
                    var run = store.run(runId);
                    if (!"QUEUED".equals(run.status())) return;
                    if (!"ENABLED".equals(job.status()) || job.version() != run.jobVersion())
                        return;
                    if (!"SYNCED".equals(job.syncStatus())) return;
                    try {
                        var key = new TriggerKey("run-" + runId, JobSchedules.GROUP);
                        if (!scheduler.checkExists(key))
                            scheduler.scheduleJob(
                                    TriggerBuilder.newTrigger()
                                            .withIdentity(key)
                                            .forJob(JobSchedules.key(job.id()))
                                            .usingJobData("runId", Long.toString(runId))
                                            .usingJobData("version", Long.toString(job.version()))
                                            .startAt(new Date(run.scheduledAt()))
                                            .withSchedule(
                                                    SimpleScheduleBuilder.simpleSchedule()
                                                            .withMisfireHandlingInstructionFireNow())
                                            .build());
                    } catch (SchedulerException e) {
                        log.warn("任务派发暂未完成 runId={} type={}", runId, e.getClass().getSimpleName());
                    }
                });
    }

    public void maintain(long staleMs) {
        // 分页遍历，不能让较早的 200 个任务永久挡住后续任务同步。
        long cursor = 0;
        while (true) {
            var ids =
                    store.db()
                            .queryForList(
                                    "SELECT id FROM sys_job WHERE id>? ORDER BY id LIMIT 200",
                                    Long.class,
                                    cursor);
            if (ids.isEmpty()) break;
            for (long id : ids) {
                sync(id);
                cursor = id;
            }
        }
        long runCursor = 0;
        while (true) {
            var ids =
                    store.db()
                            .queryForList(
                                    "SELECT id FROM sys_job_execution WHERE status='QUEUED' AND"
                                        + " id>? ORDER BY id LIMIT 200",
                                    Long.class,
                                    runCursor);
            if (ids.isEmpty()) break;
            for (long run : ids) {
                dispatch(run);
                runCursor = run;
            }
        }
        long now = System.currentTimeMillis();
        for (long id :
                store.db()
                        .queryForList(
                                "SELECT DISTINCT job_id FROM sys_job_execution WHERE status IN"
                                    + " ('RUNNING','TIMED_OUT') AND finished_at IS NULL AND"
                                    + " heartbeat_at<?",
                                Long.class,
                                now - staleMs)) {
            tx.executeWithoutResult(
                    status -> {
                        var job = store.job(id, true);
                        if (job.activeRunId() == null) return;
                        int changed =
                                store.db()
                                        .update(
                                                "UPDATE sys_job_execution SET"
                                                    + " status='UNKNOWN',summary='执行节点失联，结果未知；确认原进程停止后才可解除阻塞'"
                                                    + " WHERE id=? AND status IN"
                                                    + " ('RUNNING','TIMED_OUT') AND finished_at IS"
                                                    + " NULL AND heartbeat_at<?",
                                                job.activeRunId(),
                                                now - staleMs);
                        if (changed == 1)
                            store.db()
                                    .update(
                                            "UPDATE sys_job SET last_status='UNKNOWN' WHERE id=?",
                                            id);
                    });
        }
        // 每轮最多清理 500 条已结束记录，不删除运行中/结果未知或仍被重试引用的记录。
        var expired =
                store.db()
                        .queryForList(
                                "SELECT e.id FROM sys_job_execution e JOIN sys_job j ON"
                                    + " j.id=e.job_id WHERE e.finished_at IS NOT NULL AND"
                                    + " e.finished_at < CAST(? AS DECIMAL(20,0)) - j.retention_days * 86400000.0 AND"
                                    + " (j.active_run_id IS NULL OR j.active_run_id<>e.id) AND NOT"
                                    + " EXISTS(SELECT 1 FROM sys_job_execution child WHERE"
                                    + " child.parent_run_id=e.id) ORDER BY e.finished_at LIMIT 500",
                                Long.class,
                                now);
        for (long id : expired)
            store.db()
                    .update(
                            "DELETE FROM sys_job_execution WHERE id=? AND finished_at IS NOT NULL",
                            id);
    }
}
