package sail.framework.web;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/** 请求日志的格式与边界，避免敏感字段和超大正文写入日志。 */
public final class RequestLogConstants {
    public static final int MAX_BODY_LENGTH = 8192;
    public static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final Pattern SENSITIVE_FIELD =
            Pattern.compile(
                    ".*(ticket|password|passwd|salt|secret|token|authorization|cookie|credential|privatekey|publickey|accesskey|apikey|apiv3key|encrypteddata).*",
                    Pattern.CASE_INSENSITIVE);

    private RequestLogConstants() {
        /* 常量命名空间。 */
    }
}
