package sail.message.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import sail.common.core.*;
import sail.file.api.FileLookup;
import sail.framework.log.OperationLog;
import sail.message.dto.MessageCenterRequests.*;
import sail.message.service.MessageCenterService;
import sail.system.api.MessageAudienceDirectory;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 发布任务、接收范围和个人回执接口，动作权限与数据归属分别校验。 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageCenterController {
    private final MessageCenterService center;
    private final MessageAudienceDirectory directory;
    private final FileLookup files;

    /** 仅暴露消息业务可用配置，不返回服务器连接或密钥。 */
    @GetMapping("/settings")
    public ApiResult<?> settings() {
        return ApiResult.ok(center.settings());
    }

    /** 分页候选人员在目录服务内应用当前操作者的数据范围。 */
    @GetMapping("/audience/users")
    @SaCheckPermission("message:send")
    public ApiResult<?> users(
            @Valid @ModelAttribute ListQuery query, @RequestParam(required = false) Long deptId) {
        return ApiResult.ok(directory.candidates(query, deptId));
    }

    /** 可选部门、角色、岗位不超出当前可接触人群。 */
    @GetMapping("/audience/options")
    @SaCheckPermission("message:send")
    public ApiResult<?> options() {
        return ApiResult.ok(directory.options());
    }

    /** 回显既有选择时仍校验数据权限，不能借编号批量枚举其他账号。 */
    @PostMapping("/audience/selected")
    @SaCheckPermission("message:send")
    public ApiResult<?> selected(
            @RequestBody @jakarta.validation.constraints.Size(max = 4000)
                    List<
                                    @jakarta.validation.constraints.NotNull
                                    @jakarta.validation.constraints.Positive Long>
                            ids) {
        return ApiResult.ok(
                ids.isEmpty()
                        ? List.of()
                        : directory.resolve(
                                new MessageAudienceDirectory.Scope(
                                        ids, List.of(), List.of(), List.of(), List.of(), false,
                                        false),
                                4000));
    }

    /** 分页查询发布记录，收件回执不混入发布列表。 */
    @GetMapping("/tasks")
    @SaCheckPermission("message:query")
    public ApiResult<?> tasks(@Valid @ModelAttribute Query query) {
        return ApiResult.ok(center.tasks(query));
    }

    /** 发送人可读取本人草稿以继续编写。 */
    @GetMapping("/tasks/{id}")
    @SaCheckPermission(
            value = {"message:query", "message:send"},
            mode = cn.dev33.satoken.annotation.SaMode.OR)
    public ApiResult<?> task(@PathVariable long id) {
        return ApiResult.ok(center.task(id));
    }

    /** 创建幂等草稿，正文与附件均由服务端校验。 */
    @PostMapping("/tasks")
    @SaCheckPermission("message:send")
    @OperationLog("创建消息草稿")
    public ApiResult<?> create(@Valid @RequestBody Draft input) {
        return ApiResult.ok(center.save(null, input));
    }

    /** 乐观版本避免覆盖他人会话中的编辑。 */
    @PutMapping("/tasks/{id}")
    @SaCheckPermission("message:send")
    @OperationLog("保存消息草稿")
    public ApiResult<?> update(@PathVariable long id, @Valid @RequestBody Draft input) {
        return ApiResult.ok(center.save(id, input));
    }

    /** 删除只允许本人未发布内容，已发布历史不可伪装删除。 */
    @DeleteMapping("/tasks/{id}")
    @SaCheckPermission("message:send")
    @OperationLog("删除消息草稿")
    public ApiResult<?> delete(@PathVariable long id) {
        center.delete(id);
        return ApiResult.ok(null);
    }

    /** 解析实际接收人并产生本次确认使用的指纹。 */
    @PostMapping("/tasks/{id}/preview")
    @SaCheckPermission("message:send")
    public ApiResult<?> preview(@PathVariable long id) {
        return ApiResult.ok(center.preview(id));
    }

    /** 定时发布额外要求对应动作授权。 */
    @PostMapping("/tasks/{id}/publish")
    @SaCheckPermission("message:send")
    @OperationLog("发布站内消息")
    public ApiResult<?> publish(@PathVariable long id, @Valid @RequestBody Publish input) {
        if (center.task(id).get("scheduledAt") != null) StpUtil.checkPermission("message:schedule");
        return ApiResult.ok(center.publish(id, input));
    }

    /** 取消排队任务需要独立权限。 */
    @PostMapping("/tasks/{id}/cancel")
    @SaCheckPermission("message:cancel")
    @OperationLog("取消消息发布")
    public ApiResult<?> cancel(@PathVariable long id) {
        return ApiResult.ok(center.transition(id, "cancel"));
    }

    /** 撤回阻断后续正文和附件读取但保留回执。 */
    @PostMapping("/tasks/{id}/withdraw")
    @SaCheckPermission("message:withdraw")
    @OperationLog("撤回消息")
    public ApiResult<?> withdraw(@PathVariable long id) {
        return ApiResult.ok(center.transition(id, "withdraw"));
    }

    /** 只重试失败接收人，不重复投递成功记录。 */
    @PostMapping("/tasks/{id}/retry")
    @SaCheckPermission("message:retry")
    @OperationLog("重试失败消息")
    public ApiResult<?> retry(@PathVariable long id) {
        return ApiResult.ok(center.transition(id, "retry"));
    }

    /** 回执由后台分页检索，不把所有用户一次返回浏览器。 */
    @GetMapping("/tasks/{id}/receipts")
    @SaCheckPermission("message:receipt:query")
    public ApiResult<?> receipts(@PathVariable long id, @Valid @ModelAttribute Query query) {
        return ApiResult.ok(center.receipts(id, query));
    }

    /** 导出逐页流式写出，并阻断电子表格公式注入。 */
    @GetMapping(value = "/tasks/{id}/receipts/export", produces = "text/csv;charset=UTF-8")
    @SaCheckPermission("message:receipt:export")
    @OperationLog("导出消息接收回执")
    public void export(
            @PathVariable long id,
            @Valid @ModelAttribute Query query,
            jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        center.task(id);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(
                "Content-Disposition", "attachment; filename=message-receipts-" + id + ".csv");
        var writer = response.getWriter();
        writer.write('\ufeff');
        writer.println("账号,姓名,部门,投递状态,阅读时间,确认时间,投递说明");
        query.setPage(1);
        query.setSize(100);
        while (true) {
            var page = center.receipts(id, query);
            for (var row : page.records())
                writer.println(
                        String.join(
                                ",",
                                List.of(
                                                "username",
                                                "nickname",
                                                "deptName",
                                                "status",
                                                "readAt",
                                                "confirmedAt",
                                                "errorMessage")
                                        .stream()
                                        .map(key -> csv(row.get(key)))
                                        .toList()));
            if (query.getPage() * query.getSize() >= page.total()) break;
            query.setPage(query.getPage() + 1);
        }
    }

    /** 导出的自由文本用双引号包围，危险前缀强制作为文本。 */
    private String csv(Object value) {
        String text = Objects.toString(value, "");
        if (text.matches("^[\\s]*[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /** 详情读取只作用于当前认证收件人并记录首次阅读。 */
    @GetMapping("/announcements")
    public ApiResult<?> announcements() {
        // 仅返回当前收件人的待展示公告编号，正文继续通过详情鉴权获取。
        return ApiResult.ok(center.announcements());
    }

    /** 展示和显式确认分别持久化，不能用前端本地存储冒充服务端回执。 */
    @PutMapping("/mine/{id}/presented")
    public ApiResult<?> presented(@PathVariable long id) {
        center.presented(id);
        return ApiResult.ok(null);
    }

    /** 详情读取只作用于当前认证收件人并记录首次阅读。 */
    @GetMapping("/mine/{id}")
    public ApiResult<?> detail(@PathVariable long id) {
        return ApiResult.ok(center.detail(id));
    }

    /** 明确确认与打开消息分开记录。 */
    @PutMapping("/mine/{id}/confirm")
    @OperationLog("确认站内消息")
    public ApiResult<?> confirm(@PathVariable long id) {
        center.confirm(id);
        return ApiResult.ok(null);
    }

    /** 个人归档和恢复只改变本人收件箱。 */
    @PutMapping("/archive")
    @OperationLog("归档或恢复个人消息")
    public ApiResult<?> archive(@Valid @RequestBody Selection input) {
        center.archive(input);
        return ApiResult.ok(null);
    }

    /** 文件访问必须命中当前有效收件关系，不能用文件编号绕过消息授权。 */
    @GetMapping("/tasks/{id}/attachments/{fileId}/preview")
    public ApiResult<?> attachmentPreview(@PathVariable long id, @PathVariable long fileId)
            throws java.io.IOException {
        // 与下载共用撤回、过期、发送者和收件人的实时授权判断。
        return ApiResult.ok(files.previewOwned(fileId, center.attachmentOwner(id, fileId)));
    }

    /** 文件访问必须命中当前有效收件关系，不能用文件编号绕过消息授权。 */
    @GetMapping("/tasks/{id}/attachments/{fileId}")
    public ResponseEntity<StreamingResponseBody> attachment(
            @PathVariable long id,
            @PathVariable long fileId,
            @RequestParam(defaultValue = "false") boolean inline) {
        long owner = center.attachmentOwner(id, fileId);
        var file = files.findOwned(fileId, owner);
        // 仅允许安全栅格图内联，其他内容一律以附件处理，避免同源主动内容执行。
        boolean image =
                inline
                        && Set.of("image/png", "image/jpeg", "image/gif", "image/webp")
                                .contains(file.contentType());
        var disposition =
                (image ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(file.name(), StandardCharsets.UTF_8)
                        .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .contentType(
                        MediaType.parseMediaType(
                                image ? file.contentType() : "application/octet-stream"))
                .body(
                        output -> {
                            try (var input = files.openOwned(fileId, owner)) {
                                input.transferTo(output);
                            }
                        });
    }
}
