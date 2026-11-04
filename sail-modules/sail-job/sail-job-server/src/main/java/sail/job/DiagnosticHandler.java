package sail.job;

import org.springframework.stereotype.Component;

import sail.common.core.BusinessException;
import sail.job.api.*;

/** 无副作用的诊断处理器，不默认创建或启用任务。 */
@Component
public class DiagnosticHandler implements JobHandler {
    public String code() {
        return "system.diagnostic";
    }

    public String name() {
        return "调度连通性检查";
    }

    public String description() {
        return "检查调度执行链路，无业务写入；参数仅支持空对象 {}。";
    }

    public boolean retryable() {
        return true;
    }

    public String validate(String parameters) {
        if (!parameters.replaceAll("\\s", "").equals("{}"))
            throw new BusinessException("连通性检查参数必须为 {}，不接收额外字段");
        return "{}";
    }

    public JobResult execute(JobContext context, String parameters) throws Exception {
        context.checkCancelled();
        return new JobResult(1, 0, "调度器与执行记录链路正常");
    }
}
