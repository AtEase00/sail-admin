package sail.job.api;

/** 仅注册的处理器可执行；参数不得包含密码、令牌或任意脚本。 */
public interface JobHandler {
    String code();

    String name();

    String description();

    default boolean retryable() {
        return false;
    }

    /** 校验并返回规范化的无秘密 JSON，写入任务配置与运行快照。 */
    String validate(String parameters);

    JobResult execute(JobContext context, String parameters) throws Exception;
}
