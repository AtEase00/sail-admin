package sail.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobMaintenance {
    private final JobSynchronizer synchronizer;

    @Value("${sail.job.enabled:true}")
    private boolean enabled;

    @Value("${sail.job.maintenance-delay-ms:5000}")
    private long delay;

    @Value("${sail.job.stale-heartbeat-ms:90000}")
    private long stale;

    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(
                    Thread.ofPlatform().daemon().name("job-maintenance").factory());

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (enabled)
            worker.scheduleWithFixedDelay(
                    () -> {
                        try {
                            synchronizer.maintain(Math.max(30000, stale));
                        } catch (Exception e) {
                            log.warn("任务维护暂不可用，下一轮重试 type={}", e.getClass().getSimpleName());
                        }
                    },
                    0,
                    Math.max(1000, delay),
                    TimeUnit.MILLISECONDS);
    }

    @jakarta.annotation.PreDestroy
    public void close() {
        worker.shutdown();
    }
}
