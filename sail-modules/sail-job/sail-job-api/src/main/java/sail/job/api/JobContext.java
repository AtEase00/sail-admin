package sail.job.api;

import java.util.function.BooleanSupplier;

/** 处理器必须定期检查取消信号；外部调用还需配置独立的连接和读取超时。 */
public record JobContext(long executionId, String traceId, BooleanSupplier cancelled) {
    public void checkCancelled() throws InterruptedException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new InterruptedException("任务收到协作取消信号");
    }
}
