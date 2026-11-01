package sail.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.Getter;
import lombok.Setter;

import sail.system.api.MessageAudienceDirectory.Scope;

import java.util.*;

/** 消息中心请求，编辑版本与收件快照明确传递，防止覆盖和重复发布。 */
public final class MessageCenterRequests {
    private MessageCenterRequests() {
        /* 请求模型命名空间。 */
    }

    /** 组织范围仅允许真实正整数编号，合并后由系统目录服务最终校验。 */
    public record Audience(
            @NotNull @Size(max = 2000) List<@NotNull @Positive Long> userIds,
            @NotNull @Size(max = 500) List<@NotNull @Positive Long> deptIds,
            @NotNull @Size(max = 500) List<@NotNull @Positive Long> roleIds,
            @NotNull @Size(max = 500) List<@NotNull @Positive Long> postIds,
            @NotNull @Size(max = 2000) List<@NotNull @Positive Long> excludedIds,
            boolean includeChildren,
            boolean all) {
        /** 转换为跨模块解析契约，语义和编号均保持原样。 */
        public Scope scope() {
            return new Scope(userIds, deptIds, roleIds, postIds, excludedIds, includeChildren, all);
        }
    }

    /** 草稿内容可留空，发布时按完整业务规则校验。 */
    public record Draft(
            @NotBlank @Size(max = 64) String requestKey,
            @Min(0) int version,
            @NotNull @Size(max = 200) String title,
            @NotNull @Size(max = 100000) String content,
            @NotBlank String category,
            @NotBlank String priority,
            boolean requireConfirmation,
            boolean pinned,
            @Size(max = 32) String scheduledAt,
            @Size(max = 32) String expiresAt,
            @Size(max = 500) String actionPath,
            @Valid @NotNull Audience audience,
            @NotNull @Size(max = 20) List<@NotNull @Positive Long> attachmentIds,
            @Size(max = 64) String templateCode,
            @Pattern(regexp = "INBOX|POPUP") String displayMode) {}

    /** 提交前端刚预览的版本和人员指纹，目录改变时必须重新确认。 */
    public record Publish(@Min(0) int version, @NotBlank String fingerprint) {}

    /** 批处理只接受明确记录，不支持任意用户参数。 */
    public record Selection(
            @NotEmpty @Size(max = 500) List<@NotNull @Positive Long> ids, boolean archived) {}

    /** 业务查询状态独立于通用布尔状态，所有筛选在后端执行。 */
    @Getter
    @Setter
    public static class Query extends sail.common.core.ListQuery {
        @Pattern(regexp = "NOTICE|BUSINESS|SYSTEM|^$")
        private String category;

        @Pattern(regexp = "NORMAL|IMPORTANT|URGENT|^$")
        private String priority;

        @Pattern(regexp = "all|unread|important|archived|^$")
        private String folder = "all";

        @Pattern(regexp = "PENDING|DELIVERED|SKIPPED|FAILED|READ|UNREAD|CONFIRMED|UNCONFIRMED|^$")
        private String receiptStatus;
    }
}
