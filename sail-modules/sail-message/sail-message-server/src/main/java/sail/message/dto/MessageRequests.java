package sail.message.dto;

import jakarta.validation.constraints.*;

import java.util.*;

/** 消息模板、发送和已读批量请求。 */
public final class MessageRequests {
    private MessageRequests() {
        /* 请求模型命名空间。 */
    }

    /** 模板变量使用 ${name} 语法，只替换明确给出的值。 */
    public record Template(
            @NotBlank @Pattern(regexp = "[a-zA-Z][a-zA-Z0-9_.-]{1,63}") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 100000) String content,
            @NotNull Boolean enabled) {}

    /** 一次最多发送 500 名明确接收人。 */
    public record Send(
            @NotBlank String templateCode,
            @NotEmpty @Size(max = 500) List<@NotNull @Positive Long> recipientIds,
            @NotNull @Size(max = 50)
                    Map<@NotBlank String, @NotNull @Size(max = 1000) String> variables) {}

    /** 当前账号消息编号集合。 */
    public record Read(@NotEmpty @Size(max = 500) List<@NotNull @Positive Long> ids) {}

    /** 模板预览显式提供变量，不在服务器执行表达式。 */
    public record TemplatePreview(
            @NotNull @Size(max = 50)
                    Map<@NotBlank String, @NotNull @Size(max = 1000) String> variables) {}
}
