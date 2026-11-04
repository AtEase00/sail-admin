package sail.job.api;

/** 摘要只能含可公开的业务结论，不回传请求凭据或原始外部响应。 */
public record JobResult(long succeeded, long failed, String summary) {
    public JobResult {
        if (succeeded < 0 || failed < 0) throw new IllegalArgumentException("处理数量不能为负数");
    }

    public String status() {
        return failed == 0 ? "SUCCESS" : succeeded == 0 ? "FAILED" : "PARTIAL";
    }
}
