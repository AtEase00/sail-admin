package sail.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.quartz.JobExecutionContext;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import sail.job.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobRunner {
    private final JobStore store;
    private final HandlerRegistry handlers;
    private final JobTransactions tx;

    @Value("${sail.job.enabled:true}")
    private boolean enabled;

    private final ScheduledExecutorService heartbeats =
            Executors.newScheduledThreadPool(
                    2, Thread.ofPlatform().daemon().name("job-heartbeat-", 0).factory());
    private final Set<AtomicBoolean> cancellations = ConcurrentHashMap.newKeySet();

    @jakarta.annotation.PreDestroy
    public void close() {
        cancellations.forEach(c -> c.set(true));
        heartbeats.shutdown();
    }

    record Work(JobModels.Definition job, JobModels.Execution run) {}

    public void execute(JobExecutionContext context) throws Exception {
        if (!enabled) return;
        var data = context.getMergedJobDataMap();
        long id = Long.parseLong(data.getString("jobId"));
        long version = Long.parseLong(data.getString("version"));
        Long runId = data.containsKey("runId") ? Long.valueOf(data.getString("runId")) : null;
        String instance = context.getScheduler().getSchedulerInstanceId();
        Work work =
                tx.execute(
                        status -> {
                            var job = store.job(id, true);
                            JobModels.Execution pending = runId == null ? null : store.run(runId);
                            if (pending != null
                                    && (!"QUEUED".equals(pending.status())
                                            || !Objects.equals(job.activeRunId(), runId)))
                                return null;
                            if (!"ENABLED".equals(job.status()) || job.version() != version) {
                                if (pending != null) skip(job, pending, "任务已停用或配置版本已变更");
                                return null;
                            }
                            if (pending == null && job.activeRunId() != null)
                                return null; // 同任务已有排队/运行/失联记录，禁止重叠。
                            long now = System.currentTimeMillis();
                            long execution =
                                    runId == null
                                            ? store.newRun(
                                                    job,
                                                    "SCHEDULED",
                                                    null,
                                                    null,
                                                    0,
                                                    null,
                                                    context.getScheduledFireTime().getTime(),
                                                    "QUEUED")
                                            : runId;
                            store.db()
                                    .update(
                                            "UPDATE sys_job_execution SET"
                                                + " status='RUNNING',started_at=?,heartbeat_at=?,instance_id=?"
                                                + " WHERE id=?",
                                            now,
                                            now,
                                            instance,
                                            execution);
                            store.db()
                                    .update(
                                            "UPDATE sys_job SET"
                                                + " active_run_id=?,last_started_at=?,last_status='RUNNING'"
                                                + " WHERE id=?",
                                            execution,
                                            now,
                                            id);
                            return new Work(job, store.run(execution));
                        });
        if (work == null) return;
        AtomicBoolean cancelled = new AtomicBoolean();
        cancellations.add(cancelled);
        ScheduledFuture<?> heartbeat =
                heartbeats.scheduleAtFixedRate(
                        () -> heartbeat(work, cancelled), 1, 1, TimeUnit.SECONDS);
        String oldTrace = MDC.get("traceId");
        MDC.put("traceId", work.run().traceId());
        JobResult result = null;
        String failure = null;
        try {
            log.info(
                    "开始任务 jobId={} runId={} handler={}", id, work.run().id(), work.job().handler());
            var handler = handlers.get(work.job().handler());
            result =
                    handler.execute(
                            new JobContext(work.run().id(), work.run().traceId(), cancelled::get),
                            handler.validate(work.job().parameters()));
            if (result == null) throw new IllegalStateException("任务处理器未返回结果");
        } catch (Exception e) {
            failure = "处理器执行失败：" + e.getClass().getSimpleName() + "，请按追踪号检查服务日志";
            // 不记录异常 message：第三方异常可能包含秘密参数或响应。
            log.warn(
                    "任务执行失败 jobId={} runId={} errorType={}",
                    id,
                    work.run().id(),
                    e.getClass().getName());
        } finally {
            heartbeat.cancel(false);
            cancellations.remove(cancelled);
            if (oldTrace == null) MDC.remove("traceId");
            else MDC.put("traceId", oldTrace);
        }
        JobResult outcome = result;
        String error = failure;
        tx.executeWithoutResult(
                status -> {
                    var job = store.job(id, true);
                    var run = store.run(work.run().id());
                    // 失联后的迟到结果不能覆盖人工处置，也不能释放其他执行持有的任务。
                    if (!Objects.equals(job.activeRunId(), run.id())
                            || !Set.of("RUNNING", "TIMED_OUT").contains(run.status())) return;
                    long now = System.currentTimeMillis();
                    boolean timeout =
                            "TIMED_OUT".equals(run.status())
                                    || now - run.startedAt() >= work.job().timeoutSeconds() * 1000L;
                    String state =
                            timeout ? "TIMED_OUT" : error != null ? "FAILED" : outcome.status();
                    String summary =
                            timeout
                                    ? "已超时，处理器现已返回；未自动重试"
                                    : error != null ? error : outcome.summary();
                    store.db()
                            .update(
                                    "UPDATE sys_job_execution SET"
                                        + " status=?,finished_at=?,duration_ms=?,succeeded=?,failed=?,summary=?"
                                        + " WHERE id=?",
                                    state,
                                    now,
                                    now - run.startedAt(),
                                    outcome == null ? 0 : outcome.succeeded(),
                                    outcome == null ? 0 : outcome.failed(),
                                    JobStore.truncate(summary, 1000),
                                    run.id());
                    Long next = null;
                    if (Set.of("FAILED", "PARTIAL").contains(state)
                            && "ENABLED".equals(job.status())
                            && job.version() == run.jobVersion()
                            && run.attempt() < job.maxRetries()
                            && handlers.get(job.handler()).retryable()) {
                        long delay =
                                job.retryDelaySeconds()
                                        * 1000L
                                        * (1L << Math.min(run.attempt(), 3));
                        next =
                                store.newRun(
                                        job,
                                        "RETRY",
                                        run.actorId(),
                                        run.actorName(),
                                        run.attempt() + 1,
                                        run.id(),
                                        now + delay,
                                        "QUEUED");
                    }
                    store.db()
                            .update(
                                    "UPDATE sys_job SET active_run_id=?,last_status=? WHERE id=?",
                                    next,
                                    state,
                                    id);
                });
    }

    private void skip(JobModels.Definition job, JobModels.Execution run, String reason) {
        store.db()
                .update(
                        "UPDATE sys_job_execution SET status='SKIPPED',finished_at=?,summary=?"
                            + " WHERE id=?",
                        System.currentTimeMillis(),
                        reason,
                        run.id());
        store.db()
                .update(
                        "UPDATE sys_job SET active_run_id=NULL WHERE id=? AND active_run_id=?",
                        job.id(),
                        run.id());
    }

    private void heartbeat(Work work, AtomicBoolean cancelled) {
        try {
            long now = System.currentTimeMillis();
            boolean timeout = now - work.run().startedAt() >= work.job().timeoutSeconds() * 1000L;
            int changed =
                    store.db()
                            .update(
                                    "UPDATE sys_job_execution SET heartbeat_at=? WHERE id=? AND"
                                        + " status IN ('RUNNING','TIMED_OUT') AND finished_at IS"
                                        + " NULL",
                                    now,
                                    work.run().id());
            if (changed == 0) {
                cancelled.set(true);
                return;
            }
            if (timeout) {
                cancelled.set(true);
                store.db()
                        .update(
                                "UPDATE sys_job_execution SET"
                                    + " status='TIMED_OUT',summary='已超时，已请求协作取消；处理器尚未返回，继续阻止重复执行'"
                                    + " WHERE id=? AND status='RUNNING'",
                                work.run().id());
                store.db()
                        .update(
                                "UPDATE sys_job SET last_status='TIMED_OUT' WHERE id=? AND"
                                    + " active_run_id=?",
                                work.job().id(),
                                work.run().id());
            }
        } catch (Exception e) {
            cancelled.set(true);
            log.warn("任务心跳写入失败 runId={} type={}", work.run().id(), e.getClass().getSimpleName());
        }
    }
}
