package sail.message.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import sail.common.core.ApiResult;
import sail.framework.log.OperationLog;
import sail.message.dto.MessageRequests;
import sail.message.service.*;

/** 消息模板、发送、个人收件箱和实时连接票据接口。 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messages;
    private final MessageSocket sockets;
    private final MessageCenterService center;
    private final sail.system.api.MessageAudienceDirectory directory;

    @GetMapping("/templates/available")
    @SaCheckPermission("message:send")
    public ApiResult<?> availableTemplates() {
        // 接口仅返回可用于当前发送操作的启用模板。
        return ApiResult.ok(messages.availableTemplates());
    }

    @GetMapping("/templates")
    @SaCheckPermission("message:template:query")
    public ApiResult<?> templates(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 授权用户查看模板。 */
        return ApiResult.ok(messages.templates(query));
    }

    /** 管理列表分页及搜索全部在数据库处理。 */
    @GetMapping("/templates/page")
    @SaCheckPermission("message:template:query")
    public ApiResult<?> templatePage(@Valid @ModelAttribute sail.common.core.ListQuery query) {
        return ApiResult.ok(messages.templatesPage(query));
    }

    @PostMapping("/templates")
    @SaCheckPermission("message:template:write")
    @OperationLog("创建消息模板")
    public ApiResult<?> create(@Valid @RequestBody MessageRequests.Template v) {
        /* 校验模板编码唯一性。 */
        return ApiResult.ok(messages.template(null, v));
    }

    @PutMapping("/templates/{id}")
    @SaCheckPermission("message:template:write")
    @OperationLog("修改消息模板")
    public ApiResult<?> update(
            @PathVariable long id, @Valid @RequestBody MessageRequests.Template v) {
        /* 历史消息不会随模板变动。 */
        return ApiResult.ok(messages.template(id, v));
    }

    @PostMapping("/send")
    @SaCheckPermission("message:send")
    @OperationLog("发送站内消息")
    public ApiResult<?> send(@Valid @RequestBody MessageRequests.Send v) {
        /* 所有接收人统一校验后批量发送。 */
        directory.resolve(
                new sail.system.api.MessageAudienceDirectory.Scope(
                        v.recipientIds(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        java.util.List.of(),
                        false,
                        false),
                500);
        return ApiResult.ok(messages.send(v));
    }

    /** 模板维护权限不隐含删除操作，发布快照始终保留。 */
    @DeleteMapping("/templates/{id}")
    @SaCheckPermission("message:template:delete")
    @OperationLog("删除消息模板")
    public ApiResult<?> deleteTemplate(@PathVariable long id) {
        messages.deleteTemplate(id);
        return ApiResult.ok(null);
    }

    /** 发送人可以选择启用模板并预览变量渲染结果。 */
    @PostMapping("/templates/{id}/preview")
    @SaCheckPermission(
            value = {"message:send", "message:template:query"},
            mode = cn.dev33.satoken.annotation.SaMode.OR)
    public ApiResult<?> previewTemplate(
            @PathVariable long id, @Valid @RequestBody MessageRequests.TemplatePreview input) {
        return ApiResult.ok(messages.previewTemplate(id, input.variables()));
    }

    @GetMapping("/mine")
    public ApiResult<?> mine(
            @jakarta.validation.Valid @ModelAttribute
                    sail.message.dto.MessageCenterRequests.Query query) {
        /* 用户仅访问自己的收件箱。 */
        return ApiResult.ok(center.mine(query));
    }

    @GetMapping("/unread-count")
    public ApiResult<?> unread() {
        /* 以持久化状态计算数量。 */
        return ApiResult.ok(center.unread());
    }

    @PutMapping("/read")
    @OperationLog("标记消息已读")
    public ApiResult<?> read(@Valid @RequestBody MessageRequests.Read v) {
        /* 批量更新仍限定当前接收人。 */
        center.read(v.ids());
        return ApiResult.ok(null);
    }

    @PutMapping("/read-all")
    @OperationLog("标记全部消息已读")
    public ApiResult<?> all() {
        /* 全部已读不会影响其他账号。 */
        center.read(null);
        return ApiResult.ok(null);
    }

    @PostMapping("/socket-ticket")
    public ApiResult<?> ticket() {
        /* 票据仅可消费一次，有效期三十秒。 */
        return ApiResult.ok(
                java.util.Map.of(
                        "ticket", sockets.ticket(), "path", "/ws/messages", "expiresIn", 30));
    }

    @GetMapping("/history")
    @SaCheckPermission("message:query")
    public ApiResult<?> history(
            @jakarta.validation.Valid @ModelAttribute sail.common.core.ListQuery query) {
        /* 单独授权的消息发送记录查询。 */
        return ApiResult.ok(messages.history(query));
    }
}
