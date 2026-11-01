package sail.message.service;

import static sail.message.service.MessageCenterRepository.*;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.*;
import sail.file.api.*;
import sail.message.dto.MessageCenterRequests.*;
import sail.system.api.MessageAudienceDirectory;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.stream.Collectors;

/** 消息发布生命周期、接收快照、个人回执与附件访问，所有操作执行实际身份校验。 */
@Service
@RequiredArgsConstructor
public class MessageCenterService implements FileReferenceGuard {
    private final MessageCenterRepository store;
    private final MessageAudienceDirectory directory;
    private final FileLookup files;
    private final MessageContent content;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;

    /** 配置必须真实存在且合法，缺失时明确拒绝，不能随意替代为其他参数。 */
    public int setting(String key) {
        var row =
                store.one(
                        "SELECT config_value FROM sys_config WHERE config_key=:key AND deleted=0",
                        Map.of("key", key));
        if (row == null || !String.valueOf(row.get("configValue")).matches("[0-9]{1,6}"))
            throw new BusinessException("消息配置缺失或不合法：" + key);
        return Integer.parseInt(row.get("configValue").toString());
    }

    /** 发布页面读取可生效配置，消息提醒开关亦供个人入口读取。 */
    public Map<String, Integer> settings() {
        return Map.of(
                "maxRecipients",
                setting("message.max_recipients"),
                "scheduleDays",
                setting("message.schedule_days"),
                "maxAttachments",
                setting("message.max_attachments"),
                "recallMinutes",
                setting("message.recall_minutes"),
                "reminderEnabled",
                setting("message.reminder_enabled"));
    }

    /** 创建或编辑草稿，乐观版本保护并保存白名单富文本与真实附件元数据。 */
    @Transactional
    public Map<String, Object> save(Long id, Draft input) {
        long actor = StpUtil.getLoginIdAsLong();
        if (!MessageConstants.CATEGORIES.contains(input.category())
                || !MessageConstants.PRIORITIES.contains(input.priority()))
            throw new BusinessException("消息分类或重要程度不合法");
        if (input.attachmentIds().size() > setting("message.max_attachments"))
            throw new BusinessException("附件数量超过配置上限");
        List<FileLookup.Attachment> attachments =
                input.attachmentIds().stream()
                        .distinct()
                        .map(file -> files.findOwned(file, actor))
                        .toList();
        String html = content.clean(input.content(), new HashSet<>(input.attachmentIds()));
        if (StrUtil.isNotBlank(input.actionPath())
                && !input.actionPath()
                        .matches(
                                "^/(?:dashboard|system/(?:users|roles|menus|depts|posts|dictionaries|configs|audit)|messages(?:/(?:history|templates|send))?|files|pay/(?:orders|applications|channels|refunds|notifications))(?:\\?[a-zA-Z0-9_.%=&-]*)?$"))
            throw new BusinessException("业务入口必须是支持的系统内部页面");
        var scheduled = parseTime(input.scheduledAt());
        var expires = parseTime(input.expiresAt());
        if (id == null) {
            var existing =
                    store.one(
                            "SELECT id FROM message_task WHERE sender_id=:sender AND"
                                    + " request_key=:key AND deleted=0",
                            Map.of("sender", actor, "key", input.requestKey()));
            if (existing != null) return task(number(existing, "id"));
        } else {
            var current = editable(id);
            if (number(current, "version") != input.version())
                throw new BusinessException(409, "草稿已被更新，请重新加载");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("sender", actor);
        args.put("key", input.requestKey());
        args.put("title", input.title().trim());
        args.put("content", html);
        args.put("summary", content.summary(html));
        args.put("category", input.category());
        args.put("priority", input.priority());
        args.put("confirmation", input.requireConfirmation());
        args.put("pinned", input.pinned());
        args.put("displayMode", input.displayMode() == null ? "INBOX" : input.displayMode());
        args.put("path", StrUtil.emptyToNull(input.actionPath()));
        args.put("template", StrUtil.emptyToNull(input.templateCode()));
        args.put("audience", json.writeValueAsString(input.audience()));
        args.put("scheduled", scheduled);
        args.put("expires", expires);
        args.put("version", input.version());
        if (id == null)
            id =
                    store.insert(
                            "INSERT INTO"
                                + " message_task(sender_id,request_key,title,content,summary,category,priority,require_confirmation,pinned,action_path,template_code,audience_json,scheduled_at,expires_at,status,display_mode)"
                                + " VALUES(:sender,:key,:title,:content,:summary,:category,:priority,:confirmation,:pinned,:path,:template,:audience,:scheduled,:expires,'DRAFT',:displayMode)",
                            args);
        else {
            args.put("id", id);
            store.update(
                    "UPDATE message_task SET"
                        + " title=:title,content=:content,summary=:summary,category=:category,priority=:priority,require_confirmation=:confirmation,pinned=:pinned,action_path=:path,template_code=:template,audience_json=:audience,scheduled_at=:scheduled,expires_at=:expires,display_mode=:displayMode,status='DRAFT',version=version+1,updated_at=CURRENT_TIMESTAMP"
                        + " WHERE id=:id AND version=:version",
                    args);
        }
        args.put("id", id);
        // 草稿附件允许替换，物理清除尚未发布的关联，不删除文件对象。
        store.update("DELETE FROM message_attachment WHERE task_id=:id", args);
        List<Map<String, ?>> rows = new ArrayList<>();
        for (var attachment : attachments)
            rows.add(
                    Map.of(
                            "task",
                            id,
                            "file",
                            attachment.id(),
                            "name",
                            attachment.name(),
                            "type",
                            attachment.contentType(),
                            "size",
                            attachment.size()));
        store.batch(
                "INSERT INTO message_attachment(task_id,file_id,name,content_type,file_size)"
                        + " VALUES(:task,:file,:name,:type,:size)",
                rows);
        return task(id);
    }

    /** 所有时间输入使用明确的常用格式，解析失败返回具体业务错误。 */
    private LocalDateTime parseTime(String value) {
        if (StrUtil.isBlank(value)) return null;
        try {
            return LocalDateTime.parse(
                    value,
                    DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                            .withResolverStyle(ResolverStyle.STRICT));
        } catch (DateTimeParseException ex) {
            throw new BusinessException("发送时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 查看或管理仅限本人任务，全量查询权限不隐含撤回、重试等动作权限。 */
    public Map<String, Object> task(long id) {
        var task = store.task(id, false);
        if (task == null) throw new BusinessException(404, "发布任务不存在");
        if (number(task, "senderId") != StpUtil.getLoginIdAsLong()
                && !StpUtil.hasPermission("message:query:all"))
            throw new BusinessException(403, "无权查看其他人的发布任务");
        task.put("audience", json.readValue(task.get("audienceJson").toString(), Audience.class));
        task.remove("audienceJson");
        task.put("attachments", attachments(id));
        task.put("statistics", statistics(id));
        return task;
    }

    /** 编辑和发送只能操作本人草稿，管理员也不能冒用他人发布身份。 */
    private Map<String, Object> editable(long id) {
        var row = store.task(id, true);
        if (row == null) throw new BusinessException(404, "发布任务不存在");
        if (number(row, "senderId") != StpUtil.getLoginIdAsLong())
            throw new BusinessException(403, "只能编辑本人消息");
        if (!MessageConstants.EDITABLE.contains(row.get("status")))
            throw new BusinessException("当前状态不能编辑，请复制为新草稿");
        return row;
    }

    /** 草稿预览由后端解析真实人群，指纹包含人员快照和当前内容版本。 */
    public Map<String, Object> preview(long id) {
        var row = task(id);
        if (!MessageConstants.EDITABLE.contains(row.get("status")))
            throw new BusinessException("仅草稿或取消的消息可以预览接收范围");
        var people =
                directory.resolve(
                        ((Audience) row.get("audience")).scope(),
                        setting("message.max_recipients"));
        return Map.of(
                "count",
                people.size(),
                "fingerprint",
                fingerprint(row, people),
                "version",
                row.get("version"),
                "sample",
                people.stream().limit(20).toList());
    }

    /** 收件快照改变时使旧预览失效，避免确认人数与实际发布人数不符。 */
    private String fingerprint(
            Map<String, Object> task, List<MessageAudienceDirectory.Person> people) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (task.get("id")
                                                            + ":"
                                                            + task.get("version")
                                                            + ":"
                                                            + json.writeValueAsString(people))
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("运行环境缺少 SHA-256", ex);
        }
    }

    /** 事务冻结确认的接收人快照，重复发布不会重复生成任务或收件记录。 */
    @Transactional
    public Map<String, Object> publish(long id, Publish input) {
        var row = store.task(id, true);
        if (row == null) throw new BusinessException(404, "发布任务不存在");
        if (number(row, "senderId") != StpUtil.getLoginIdAsLong())
            throw new BusinessException(403, "只能发布本人消息");
        if (!MessageConstants.EDITABLE.contains(row.get("status"))) {
            if (input.fingerprint().equals(row.get("snapshotHash"))) return task(id);
            throw new BusinessException(409, "消息已经发布或状态已改变");
        }
        if (number(row, "version") != input.version())
            throw new BusinessException(409, "草稿内容已改变，请重新预览");
        if (StrUtil.isBlank((String) row.get("title"))
                || content.empty((String) row.get("content")))
            throw new BusinessException("请填写消息标题和正文");
        var now = LocalDateTime.now();
        var scheduled = time(row, "scheduledAt");
        var expires = time(row, "expiresAt");
        if (scheduled != null
                && (scheduled.isBefore(now)
                        || scheduled.isAfter(now.plusDays(setting("message.schedule_days")))))
            throw new BusinessException("定时发送必须晚于当前时间且不超过配置天数");
        if (expires != null && !expires.isAfter(scheduled == null ? now : scheduled))
            throw new BusinessException("有效期必须晚于实际发送时间");
        var audience = json.readValue(row.get("audienceJson").toString(), Audience.class);
        var people = directory.resolve(audience.scope(), setting("message.max_recipients"));
        if (!input.fingerprint().equals(fingerprint(row, people)))
            throw new BusinessException(409, "接收人范围已发生变化，请重新预览确认");
        // 发布前再次核对附件状态，草稿期间被删除的对象不能进入发布快照。
        for (var attachment : attachments(id))
            files.findOwned(number(attachment, "fileId"), number(row, "senderId"));
        List<Map<String, ?>> batch = new ArrayList<>();
        for (var person : people) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("task", id);
            item.put("user", person.id());
            item.put("username", person.username());
            item.put("nickname", person.nickname());
            item.put("dept", person.deptName());
            batch.add(item);
            if (batch.size() == MessageConstants.BATCH_SIZE) {
                insertRecipients(batch);
                batch.clear();
            }
        }
        insertRecipients(batch);
        store.update(
                "UPDATE message_task SET"
                    + " status=:status,published_at=:now,next_attempt_at=:next,snapshot_hash=:hash,version=version+1,attempts=0,last_error=NULL,updated_at=:now"
                    + " WHERE id=:id",
                Map.of(
                        "status",
                        scheduled == null ? "QUEUED" : "SCHEDULED",
                        "now",
                        now,
                        "next",
                        scheduled == null ? now : scheduled,
                        "hash",
                        input.fingerprint(),
                        "id",
                        id));
        return task(id);
    }

    /** 每批不超过固定写入大小，快照落库与发布状态在同一事务提交。 */
    private void insertRecipients(List<Map<String, ?>> batch) {
        store.batch(
                "INSERT INTO message_recipient(task_id,user_id,username,nickname,dept_name)"
                        + " VALUES(:task,:user,:username,:nickname,:dept)",
                batch);
    }

    /** 管理列表按发布任务展示，统计关联收件回执而不是前端计算。 */
    public PageResult<Map<String, Object>> tasks(Query query) {
        Map<String, Object> args = new LinkedHashMap<>();
        String where =
                "FROM message_task t LEFT JOIN sys_user u ON u.id=t.sender_id WHERE t.deleted=0";
        if (!StpUtil.hasPermission("message:query:all")) {
            where += " AND t.sender_id=:actor";
            args.put("actor", StpUtil.getLoginIdAsLong());
        }
        where = filter(where, args, query, "t");
        if (StrUtil.isNotBlank(query.getStatus())) {
            where += " AND t.status=:state";
            args.put("state", query.getStatus());
        }
        var page =
                store.page(
                        "SELECT"
                            + " t.id,t.title,t.summary,t.category,t.priority,t.status,t.display_mode,t.sender_id,u.nickname"
                            + " AS sender_name,t.scheduled_at,t.published_at,t.created_at,t.updated_at,t.version,t.require_confirmation",
                        where,
                        args,
                        query,
                        "ORDER BY t.id DESC");
        if (page.records().isEmpty()) return page;
        // 本页回执一次分组统计，避免每条发布记录单独执行查询。
        var statistics =
                store
                        .list(
                                "SELECT r.task_id,COUNT(*) AS total,SUM(CASE WHEN"
                                    + " r.status='DELIVERED' THEN 1 ELSE 0 END) AS"
                                    + " delivered,SUM(CASE WHEN r.status='PENDING' THEN 1 ELSE 0"
                                    + " END) AS pending,SUM(CASE WHEN r.status='FAILED' THEN 1 ELSE"
                                    + " 0 END) AS failed,SUM(CASE WHEN r.status='SKIPPED' THEN 1"
                                    + " ELSE 0 END) AS skipped,SUM(CASE WHEN m.read_flag=TRUE THEN"
                                    + " 1 ELSE 0 END) AS read_count,SUM(CASE WHEN m.confirmed_at IS"
                                    + " NOT NULL THEN 1 ELSE 0 END) AS confirmed FROM"
                                    + " message_recipient r LEFT JOIN message_record m ON"
                                    + " m.task_id=r.task_id AND m.recipient_id=r.user_id AND"
                                    + " m.deleted=0 WHERE r.task_id IN (:ids) AND r.deleted=0 GROUP"
                                    + " BY r.task_id",
                                Map.of(
                                        "ids",
                                        page.records().stream()
                                                .map(row -> number(row, "id"))
                                                .toList()))
                        .stream()
                        .collect(Collectors.toMap(row -> number(row, "taskId"), row -> row));
        page.records()
                .forEach(
                        row ->
                                row.put(
                                        "statistics",
                                        statistics.getOrDefault(
                                                number(row, "id"),
                                                Map.of(
                                                        "total",
                                                        0,
                                                        "delivered",
                                                        0,
                                                        "pending",
                                                        0,
                                                        "failed",
                                                        0,
                                                        "skipped",
                                                        0,
                                                        "readCount",
                                                        0,
                                                        "confirmed",
                                                        0))));
        return page;
    }

    /** 公共筛选只拼固定列名，关键词和时间均绑定到 SQL。 */
    private String filter(String where, Map<String, Object> args, Query query, String alias) {
        var range = query.createdRange();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            where += " AND " + alias + ".title LIKE :keyword";
            args.put("keyword", "%" + query.getKeyword() + "%");
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            where += " AND " + alias + ".category=:category";
            args.put("category", query.getCategory());
        }
        if (StrUtil.isNotBlank(query.getPriority())) {
            where += " AND " + alias + ".priority=:priority";
            args.put("priority", query.getPriority());
        }
        if (range.from() != null) {
            where += " AND " + alias + ".created_at>=:from";
            args.put("from", range.from());
        }
        if (range.to() != null) {
            where += " AND " + alias + ".created_at<=:to";
            args.put("to", range.to());
        }
        return where;
    }

    /** 阅读、确认和投递分别统计，不能把 WebSocket 在线当成送达或阅读。 */
    public Map<String, Object> statistics(long id) {
        return store.one(
                "SELECT COUNT(*) AS total,COALESCE(SUM(CASE WHEN r.status='DELIVERED' THEN 1 ELSE 0"
                    + " END),0) AS delivered,COALESCE(SUM(CASE WHEN r.status='PENDING' THEN 1 ELSE"
                    + " 0 END),0) AS pending,COALESCE(SUM(CASE WHEN r.status='FAILED' THEN 1 ELSE 0"
                    + " END),0) AS failed,COALESCE(SUM(CASE WHEN r.status='SKIPPED' THEN 1 ELSE 0"
                    + " END),0) AS skipped,COALESCE(SUM(CASE WHEN m.read_flag=TRUE THEN 1 ELSE 0"
                    + " END),0) AS read_count,COALESCE(SUM(CASE WHEN m.confirmed_at IS NOT NULL"
                    + " THEN 1 ELSE 0 END),0) AS confirmed FROM message_recipient r LEFT JOIN"
                    + " message_record m ON m.task_id=r.task_id AND m.recipient_id=r.user_id AND"
                    + " m.deleted=0 WHERE r.task_id=:id AND r.deleted=0",
                Map.of("id", id));
    }

    /** 接收详情按快照分页，姓名账号部门和回执真实关联。 */
    public PageResult<Map<String, Object>> receipts(long id, Query query) {
        task(id);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", id);
        String where =
                "FROM message_recipient r LEFT JOIN message_record m ON m.task_id=r.task_id AND"
                        + " m.recipient_id=r.user_id AND m.deleted=0 WHERE r.task_id=:id AND"
                        + " r.deleted=0";
        if (StrUtil.isNotBlank(query.getKeyword())) {
            where += " AND (r.nickname LIKE :keyword OR r.username LIKE :keyword)";
            args.put("keyword", "%" + query.getKeyword() + "%");
        }
        String state = query.getReceiptStatus();
        if (StrUtil.isNotBlank(state)) {
            if (Set.of("READ", "UNREAD").contains(state))
                where +=
                        " AND r.status='DELIVERED' AND m.read_flag="
                                + (state.equals("READ") ? "TRUE" : "FALSE");
            else if (Set.of("CONFIRMED", "UNCONFIRMED").contains(state))
                where +=
                        " AND r.status='DELIVERED' AND m.confirmed_at IS "
                                + (state.equals("CONFIRMED") ? "NOT NULL" : "NULL");
            else {
                where += " AND r.status=:state";
                args.put("state", state);
            }
        }
        return store.page(
                "SELECT r.*,m.read_flag,m.read_at,m.confirmed_at",
                where,
                args,
                query,
                "ORDER BY r.id");
    }

    /** 取消、撤回和重试互相受任务行锁保护，不能与投递竞争生成重复消息。 */
    @Transactional
    public Map<String, Object> transition(long id, String action) {
        var row = store.task(id, true);
        if (row == null) throw new BusinessException(404, "发布任务不存在");
        if (number(row, "senderId") != StpUtil.getLoginIdAsLong()
                && !StpUtil.hasPermission("message:query:all"))
            throw new BusinessException(403, "无权管理该消息");
        String status = row.get("status").toString();
        var now = LocalDateTime.now();
        switch (action) {
            case "cancel" -> {
                if (!Set.of("SCHEDULED", "QUEUED").contains(status)
                        || number(
                                        store.one(
                                                "SELECT COUNT(*) AS total FROM message_recipient"
                                                    + " WHERE task_id=:id AND status<>'PENDING' AND"
                                                    + " deleted=0",
                                                Map.of("id", id)),
                                        "total")
                                > 0) throw new BusinessException("仅尚未执行的任务可以取消，已开始投递请使用撤回");
                store.update(
                        "DELETE FROM message_recipient WHERE task_id=:id AND status='PENDING'",
                        Map.of("id", id));
                status = "CANCELLED";
            }
            case "withdraw" -> {
                if (!MessageConstants.RECALLABLE.contains(status))
                    throw new BusinessException("当前状态不能撤回");
                if (time(row, "publishedAt")
                        .plusMinutes(setting("message.recall_minutes"))
                        .isBefore(now)) throw new BusinessException("消息已超过配置的撤回时限");
                status = "WITHDRAWN";
                store.update(
                        "UPDATE message_task SET withdrawn_at=:now WHERE id=:id",
                        Map.of("now", now, "id", id));
                store.update(
                        "UPDATE message_recipient SET notified=FALSE WHERE task_id=:id AND"
                                + " status='DELIVERED'",
                        Map.of("id", id));
                store.update(
                        "UPDATE message_recipient SET"
                            + " status='SKIPPED',error_message='发布者已撤回',updated_at=CURRENT_TIMESTAMP"
                            + " WHERE task_id=:id AND status='PENDING'",
                        Map.of("id", id));
            }
            case "retry" -> {
                if (!Set.of("FAILED", "PARTIAL_FAILED").contains(status))
                    throw new BusinessException("仅失败任务可重试");
                if (time(row, "expiresAt") != null && !time(row, "expiresAt").isAfter(now))
                    throw new BusinessException("消息已过期");
                store.update(
                        "UPDATE message_recipient SET status='PENDING',error_message=NULL WHERE"
                                + " task_id=:id AND status='FAILED'",
                        Map.of("id", id));
                status = "QUEUED";
            }
            default -> throw new BusinessException("不支持的消息操作");
        }
        store.update(
                "UPDATE message_task SET"
                    + " status=:state,version=version+1,attempts=0,next_attempt_at=:now,updated_at=:now"
                    + " WHERE id=:id",
                Map.of("state", status, "now", now, "id", id));
        return task(id);
    }

    /** 删除只作用于本人未发布草稿，已发布记录保留追溯。 */
    @Transactional
    public void delete(long id) {
        editable(id);
        store.update(
                "UPDATE message_task SET deleted=id,updated_at=CURRENT_TIMESTAMP WHERE id=:id",
                Map.of("id", id));
    }

    /** 收件箱仅允许当前认证账号，撤回和过期消息保留状态但隐藏内容。 */
    public PageResult<Map<String, Object>> mine(Query query) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("actor", StpUtil.getLoginIdAsLong());
        String where =
                "FROM message_record m LEFT JOIN message_task t ON t.id=m.task_id LEFT JOIN"
                    + " sys_user u ON u.id=m.sender_id WHERE m.deleted=0 AND m.recipient_id=:actor";
        boolean archived = "archived".equals(query.getFolder());
        where += " AND m.archived=" + (archived ? "TRUE" : "FALSE");
        if ("unread".equals(query.getFolder()))
            where +=
                    " AND m.read_flag=FALSE AND (t.id IS NULL OR (t.status<>'WITHDRAWN' AND"
                            + " (t.expires_at IS NULL OR t.expires_at>CURRENT_TIMESTAMP)))";
        if ("important".equals(query.getFolder()))
            where += " AND t.priority IN ('IMPORTANT','URGENT')";
        if (StrUtil.isNotBlank(query.getStatus())) {
            where += " AND m.read_flag=:read";
            args.put("read", query.booleanStatus());
        }
        var range = query.createdRange();
        if (StrUtil.isNotBlank(query.getKeyword())) {
            where += " AND COALESCE(t.title,m.title) LIKE :keyword";
            args.put("keyword", "%" + query.getKeyword() + "%");
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            where += " AND t.category=:category";
            args.put("category", query.getCategory());
        }
        if (StrUtil.isNotBlank(query.getPriority())) {
            where += " AND t.priority=:priority";
            args.put("priority", query.getPriority());
        }
        if (range.from() != null) {
            where += " AND m.created_at>=:from";
            args.put("from", range.from());
        }
        if (range.to() != null) {
            where += " AND m.created_at<=:to";
            args.put("to", range.to());
        }
        String select =
                "SELECT m.id,m.task_id,m.sender_id,u.nickname AS"
                    + " sender_name,m.read_flag,m.read_at,m.confirmed_at,m.archived,m.created_at,COALESCE(t.title,m.title)"
                    + " AS title,t.summary,t.category,t.priority,t.pinned,t.require_confirmation,t.display_mode,t.expires_at,t.status"
                    + " AS task_status";
        var result =
                store.page(
                        select,
                        where,
                        args,
                        query,
                        "ORDER BY COALESCE(t.pinned,FALSE) DESC,m.id DESC");
        result.records()
                .forEach(
                        row -> {
                            if (unavailable(row)) row.put("summary", null);
                        });
        return result;
    }

    /** 撤回与过期不再计入未读，归档亦不继续打扰接收人。 */
    public long unread() {
        return number(
                store.one(
                        "SELECT COUNT(*) AS total FROM message_record m LEFT JOIN message_task t ON"
                                + " t.id=m.task_id WHERE m.deleted=0 AND m.recipient_id=:actor AND"
                                + " m.read_flag=FALSE AND m.archived=FALSE AND (t.id IS NULL OR"
                                + " (t.status<>'WITHDRAWN' AND (t.expires_at IS NULL OR"
                                + " t.expires_at>CURRENT_TIMESTAMP)))",
                        Map.of("actor", StpUtil.getLoginIdAsLong())),
                "total");
    }

    /** 阅读详情时记录首次阅读事实，不把管理员查看发布详情当作接收人阅读。 */
    @Transactional
    public Map<String, Object> detail(long id) {
        var row =
                store.one(
                        "SELECT m.*,u.nickname AS sender_name,t.status AS task_status,t.expires_at"
                            + " FROM message_record m LEFT JOIN message_task t ON t.id=m.task_id"
                            + " LEFT JOIN sys_user u ON u.id=m.sender_id WHERE m.id=:id AND"
                            + " m.recipient_id=:actor AND m.deleted=0",
                        Map.of("id", id, "actor", StpUtil.getLoginIdAsLong()));
        if (row == null) throw new BusinessException(404, "消息不存在或不属于当前账号");
        // 与撤回共享任务行锁，显式确认不能在撤回后写入新的确认事实。
        if (row.get("taskId") != null) {
            var task = store.task(number(row, "taskId"), true);
            for (String key :
                    List.of(
                            "title",
                            "content",
                            "category",
                            "priority",
                            "requireConfirmation",
                            "actionPath",
                            "pinned",
                            "displayMode",
                            "expiresAt")) row.put(key, task.get(key));
            row.put("taskStatus", task.get("status"));
            row.put("attachments", attachments(number(row, "taskId")));
        } else {
            row.put(
                    "content",
                    new org.jsoup.nodes.TextNode(Objects.toString(row.get("content"), ""))
                            .outerHtml()
                            .replace("\n", "<br>"));
            row.put("attachments", List.of());
        }
        if (unavailable(row)) {
            row.put("content", null);
            row.put("attachments", List.of());
            return row;
        }
        read(List.of(id));
        row.put("readFlag", true);
        var receipt =
                store.one("SELECT read_at FROM message_record WHERE id=:id", Map.of("id", id));
        row.put("readAt", receipt.get("readAt"));
        return row;
    }

    /** 不可访问的内容只显示状态，不能通过已知编号绕过撤回或有效期。 */
    private boolean unavailable(Map<String, Object> row) {
        return "WITHDRAWN".equals(row.get("taskStatus"))
                || (time(row, "expiresAt") != null
                        && !time(row, "expiresAt").isAfter(LocalDateTime.now()));
    }

    /** 弹窗公告独立于已读状态，登录、重连和轮询均能补取未展示的有效公告。 */
    public List<Map<String, Object>> announcements() {
        return store.list(
                "SELECT m.id,t.title,t.priority FROM message_record m JOIN message_task t ON"
                    + " t.id=m.task_id WHERE m.deleted=0 AND t.deleted=0 AND m.recipient_id=:actor"
                    + " AND m.archived=FALSE AND m.popup_seen_at IS NULL AND t.display_mode='POPUP'"
                    + " AND t.status<>'WITHDRAWN' AND (t.expires_at IS NULL OR"
                    + " t.expires_at>CURRENT_TIMESTAMP) ORDER BY CASE t.priority WHEN 'URGENT' THEN"
                    + " 0 WHEN 'IMPORTANT' THEN 1 ELSE 2 END,m.id ASC LIMIT 10",
                Map.of("actor", StpUtil.getLoginIdAsLong()));
    }

    /** 真正展示成功后记录一次展示时间，不能替代用户主动确认；重复回执保持幂等。 */
    @Transactional
    public void presented(long id) {
        var row = detail(id);
        if (!"POPUP".equals(row.get("displayMode")) || unavailable(row))
            throw new BusinessException("此消息不是有效弹窗公告");
        store.update(
                "UPDATE message_record SET popup_seen_at=CURRENT_TIMESTAMP WHERE id=:id AND"
                    + " recipient_id=:actor AND popup_seen_at IS NULL AND deleted=0",
                Map.of("id", id, "actor", StpUtil.getLoginIdAsLong()));
    }

    /** 批量已读只更新当前用户未读且有效的消息，首次时间不覆盖。 */
    @Transactional
    public void read(List<Long> ids) {
        Map<String, Object> args = new HashMap<>();
        args.put("actor", StpUtil.getLoginIdAsLong());
        String sql =
                "UPDATE message_record SET"
                    + " read_flag=TRUE,read_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE"
                    + " recipient_id=:actor AND deleted=0 AND read_flag=FALSE AND (task_id IS NULL"
                    + " OR task_id IN (SELECT id FROM message_task WHERE status<>'WITHDRAWN' AND"
                    + " (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)))";
        if (ids != null) {
            if (ids.isEmpty()) return;
            sql += " AND id IN (:ids)";
            args.put("ids", ids);
        }
        // 仅真实未读状态变化才通知，重复查看详情不重复推送或触发客户端刷新循环。
        if (store.update(sql, args) > 0)
            events.publishEvent(new MessageService.Sent(List.of(StpUtil.getLoginIdAsLong())));
    }

    /** 确认必须是显式动作且消息确实要求确认，不用已读代替确认。 */
    @Transactional
    public void confirm(long id) {
        var row = detail(id);
        if (unavailable(row)) throw new BusinessException("消息已撤回或过期，不能确认");
        if (!flag(row, "requireConfirmation")) throw new BusinessException("此消息无需确认");
        store.update(
                "UPDATE message_record SET"
                    + " confirmed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=:id"
                    + " AND recipient_id=:actor AND confirmed_at IS NULL",
                Map.of("id", id, "actor", StpUtil.getLoginIdAsLong()));
    }

    /** 归档只影响当前用户，不删除其他接收人的记录或发布历史。 */
    @Transactional
    public void archive(Selection input) {
        store.update(
                "UPDATE message_record SET archived=:archived,updated_at=CURRENT_TIMESTAMP WHERE"
                        + " recipient_id=:actor AND id IN (:ids) AND deleted=0",
                Map.of(
                        "archived",
                        input.archived(),
                        "actor",
                        StpUtil.getLoginIdAsLong(),
                        "ids",
                        input.ids()));
        events.publishEvent(new MessageService.Sent(List.of(StpUtil.getLoginIdAsLong())));
    }

    /** 任务附件清单来自发布快照，前端不接触实际存储路径。 */
    private List<Map<String, Object>> attachments(long id) {
        return store.list(
                "SELECT file_id,name,content_type,file_size FROM message_attachment WHERE"
                        + " task_id=:id AND deleted=0 ORDER BY id",
                Map.of("id", id));
    }

    /** 附件下载需有效收件关系或发送人身份，并检查撤回和有效期。 */
    public long attachmentOwner(long taskId, long fileId) {
        var row = store.task(taskId, false);
        if (row == null) throw new BusinessException(404, "消息不存在");
        if ("WITHDRAWN".equals(row.get("status"))
                || (time(row, "expiresAt") != null
                        && !time(row, "expiresAt").isAfter(LocalDateTime.now())))
            throw new BusinessException(403, "消息已撤回或过期");
        long actor = StpUtil.getLoginIdAsLong();
        if (number(row, "senderId") != actor
                && !StpUtil.hasPermission("message:query:all")
                && store.one(
                                "SELECT id FROM message_record WHERE task_id=:task AND"
                                        + " recipient_id=:actor AND deleted=0",
                                Map.of("task", taskId, "actor", actor))
                        == null) throw new BusinessException(403, "没有附件访问权限");
        if (store.one(
                        "SELECT id FROM message_attachment WHERE task_id=:task AND file_id=:file"
                                + " AND deleted=0",
                        Map.of("task", taskId, "file", fileId))
                == null) throw new BusinessException(404, "附件不存在");
        return number(row, "senderId");
    }

    @Override
    public void checkDelete(long fileId) {
        // 草稿和有效发布均保护附件，撤回、删除或过期后才可清理物理对象。
        var row =
                store.one(
                        "SELECT a.id FROM message_attachment a JOIN message_task t ON"
                            + " t.id=a.task_id WHERE a.file_id=:id AND a.deleted=0 AND t.deleted=0"
                            + " AND t.status<>'WITHDRAWN' AND (t.expires_at IS NULL OR"
                            + " t.expires_at>CURRENT_TIMESTAMP) LIMIT 1",
                        Map.of("id", fileId));
        if (row != null) throw new BusinessException("文件仍被消息引用，请先解除草稿引用或撤回消息");
    }
}
