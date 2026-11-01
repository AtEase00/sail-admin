package sail.message.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.*;
import sail.common.core.ListQuery;
import sail.framework.mybatis.DirectoryQueries;
import sail.message.dto.MessageRequests;
import sail.message.entity.*;
import sail.message.mapper.*;
import sail.system.api.UserDirectory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

/** 模板发送和当前账号消息状态，WebSocket 只作提醒且数据库保留完整消息。 */
@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageTemplateMapper templates;
    private final UserMessageMapper messages;
    private final MessageBatchMapper batches;
    private final sail.common.core.ActorProvider actor;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final MessageContent safeContent;

    /** 事务提交后向这些接收人发送未读变化提醒。 */
    public record Sent(List<Long> recipients) {}

    public List<MessageTemplate> templates(ListQuery query) {
        var wrapper = DirectoryQueries.<MessageTemplate>from(query);
        var status = query.booleanStatus();
        wrapper.eq(status != null, "enabled", status)
                .and(
                        StrUtil.isNotBlank(query.getKeyword()),
                        q ->
                                q.like("name", query.getKeyword())
                                        .or()
                                        .like("code", query.getKeyword())
                                        .or()
                                        .like("title", query.getKeyword())
                                        .or()
                                        .like("content", query.getKeyword()));
        /* 模板列表供授权管理员维护。 */
        return templates.selectList(wrapper);
    }

    /** 模板管理使用数据库分页，发送选择器仍只读取启用模板。 */
    public PageResult<MessageTemplate> templatesPage(ListQuery query) {
        var wrapper = DirectoryQueries.<MessageTemplate>from(query);
        var status = query.booleanStatus();
        wrapper.eq(status != null, "enabled", status)
                .and(
                        StrUtil.isNotBlank(query.getKeyword()),
                        q ->
                                q.like("name", query.getKeyword())
                                        .or()
                                        .like("code", query.getKeyword())
                                        .or()
                                        .like("title", query.getKeyword()));
        var page = templates.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        return new PageResult<>(
                page.getTotal(), query.getPage(), query.getSize(), page.getRecords());
    }

    public List<MessageTemplate> availableTemplates() {
        // 发送权限只需要读取已启用模板，不要求具备模板管理权限。
        return templates.selectList(
                Wrappers.<MessageTemplate>lambdaQuery()
                        .eq(MessageTemplate::getEnabled, true)
                        .orderByAsc(MessageTemplate::getId));
    }

    public long template(Long id, MessageRequests.Template v) {
        MessageTemplate e = id == null ? new MessageTemplate() : templates.selectById(id);
        if (e == null) throw new BusinessException("消息模板不存在");
        // 模板编码稳定且唯一，历史消息保留当时渲染内容。
        if (templates.selectCount(
                        Wrappers.<MessageTemplate>lambdaQuery()
                                .eq(MessageTemplate::getCode, v.code())
                                .ne(id != null, MessageTemplate::getId, id))
                > 0) throw new BusinessException("模板编码已存在");
        e.setCode(v.code());
        e.setName(v.name());
        e.setTitle(v.title());
        e.setContent(safeContent.clean(v.content(), Set.of()));
        e.setEnabled(v.enabled());
        if (id == null) templates.insert(e);
        else templates.updateById(e);
        return e.getId();
    }

    /** 模板删除不触碰已发送的内容快照，逻辑删除仍使用主键。 */
    public void deleteTemplate(long id) {
        if (templates.selectById(id) == null) throw new BusinessException(404, "消息模板不存在");
        templates.deleteById(id);
    }

    /** 发布前使用服务端转义后的变量渲染，避免变量注入 HTML。 */
    public Map<String, String> previewTemplate(long id, Map<String, String> variables) {
        var template = templates.selectById(id);
        if (template == null
                || !Boolean.TRUE.equals(template.getEnabled())
                        && !StpUtil.hasPermission("message:template:query"))
            throw new BusinessException("模板不存在或已停用");
        Map<String, String> escaped = new HashMap<>();
        variables.forEach((key, value) -> escaped.put(key, org.jsoup.nodes.Entities.escape(value)));
        return Map.of(
                "title",
                render(template.getTitle(), variables),
                "content",
                safeContent.clean(render(template.getContent(), escaped), Set.of()));
    }

    @Transactional
    public int send(MessageRequests.Send v) {
        MessageTemplate t =
                templates.selectOne(
                        Wrappers.<MessageTemplate>lambdaQuery()
                                .eq(MessageTemplate::getCode, v.templateCode()));
        if (t == null || !Boolean.TRUE.equals(t.getEnabled()))
            throw new BusinessException("模板不存在或已停用");
        List<Long> ids = v.recipientIds().stream().distinct().toList();
        // 所有接收人校验成功后一次性发送，不创建部分成功消息。
        if (!users.areActive(ids)) throw new BusinessException("接收人包含不存在或停用用户");
        String title = render(t.getTitle(), v.variables()),
                content = render(t.getContent(), v.variables());
        if (title.length() > 200 || content.length() > 4000)
            throw new BusinessException("消息渲染后超过长度限制");
        List<UserMessage> records =
                ids.stream()
                        .map(
                                id -> {
                                    UserMessage e = new UserMessage();
                                    e.setRecipientId(id);
                                    e.setSenderId(actor.currentUserId());
                                    e.setTemplateCode(t.getCode());
                                    e.setTitle(title);
                                    e.setContent(content);
                                    e.setReadFlag(false);
                                    return e;
                                })
                        .toList();
        int count = batches.insertAll(records);
        events.publishEvent(new Sent(ids));
        return count;
    }

    public PageResult<UserMessage> mine(sail.common.core.ListQuery query) {
        // 分页及检索条件共用请求模型，权限范围仍由当前会话决定。
        long page = query.getPage(), size = query.getSize();
        String keyword = query.getKeyword();
        var range = query.createdRange();
        // 接收人只能来自当前会话，不接收请求参数中的 userId。
        var p =
                messages.selectPage(
                        new Page<>(page, size),
                        Wrappers.<UserMessage>lambdaQuery()
                                .ge(range.from() != null, UserMessage::getCreatedAt, range.from())
                                .le(range.to() != null, UserMessage::getCreatedAt, range.to())
                                .and(
                                        cn.hutool.core.util.StrUtil.isNotBlank(keyword),
                                        condition -> condition.like(UserMessage::getTitle, keyword))
                                .eq(
                                        query.booleanStatus() != null,
                                        UserMessage::getReadFlag,
                                        query.booleanStatus())
                                .eq(UserMessage::getRecipientId, StpUtil.getLoginIdAsLong())
                                .orderByDesc(UserMessage::getId));
        return new PageResult<>(p.getTotal(), page, size, p.getRecords());
    }

    public long unread() {
        /* 未读数量以数据库为准。 */
        return messages.selectCount(
                Wrappers.<UserMessage>lambdaQuery()
                        .eq(UserMessage::getRecipientId, StpUtil.getLoginIdAsLong())
                        .eq(UserMessage::getReadFlag, false));
    }

    public void read(List<Long> ids) {
        // 即使传入他人消息编号也不会修改他人状态。
        messages.update(
                null,
                Wrappers.<UserMessage>lambdaUpdate()
                        .eq(UserMessage::getRecipientId, StpUtil.getLoginIdAsLong())
                        .eq(UserMessage::getReadFlag, false)
                        .in(ids != null, UserMessage::getId, ids)
                        .set(UserMessage::getReadFlag, true)
                        .set(UserMessage::getReadAt, LocalDateTime.now()));
    }

    private String render(String source, Map<String, String> vars) {
        // 不执行 SpEL 或脚本，缺少变量直接报错而不是替换为空。
        Matcher matcher = Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_]*)}").matcher(source);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String value = vars.get(matcher.group(1));
            if (value == null) throw new BusinessException("缺少模板变量：" + matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public PageResult<UserMessage> history(sail.common.core.ListQuery query) {
        // 分页及检索条件共用请求模型，权限范围仍由当前会话决定。
        long page = query.getPage(), size = query.getSize();
        String keyword = query.getKeyword();
        var range = query.createdRange();
        // 管理员发送记录按消息实例分页，不触碰个人已读状态。
        var p =
                messages.selectPage(
                        new Page<>(page, size),
                        Wrappers.<UserMessage>lambdaQuery()
                                .ge(range.from() != null, UserMessage::getCreatedAt, range.from())
                                .le(range.to() != null, UserMessage::getCreatedAt, range.to())
                                .and(
                                        cn.hutool.core.util.StrUtil.isNotBlank(keyword),
                                        condition -> condition.like(UserMessage::getTitle, keyword))
                                .eq(
                                        query.booleanStatus() != null,
                                        UserMessage::getReadFlag,
                                        query.booleanStatus())
                                .orderByDesc(UserMessage::getId));
        return new PageResult<>(p.getTotal(), page, size, p.getRecords());
    }
}
