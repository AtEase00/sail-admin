package sail.message.service;

import cn.dev33.satoken.stp.StpUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** 一次性票据认证与多实例 Redis 通知，数据库负责离线消息补偿。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageSocket extends TextWebSocketHandler {
    private final StringRedisTemplate redis;
    private final sail.system.api.UserDirectory users;
    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final RedisScript<String> consumeTicket =
            RedisScript.of(new ClassPathResource("redis/consume-message-ticket.lua"), String.class);

    @Value("${sail.redis.namespace:sail}")
    private String namespace;

    public String ticket() {
        // URL 中传递短时一次性票据，避免长期登录令牌进入代理日志。
        String ticket = UUID.randomUUID().toString();
        redis.opsForValue()
                .set(
                        namespace + ":ws:" + ticket,
                        StpUtil.getLoginIdAsLong() + "|" + StpUtil.getTokenValue(),
                        Duration.ofSeconds(30));
        return ticket;
    }

    public Long consume(String ticket) {
        // Redis 原子取走票据，禁止重放和跨实例重复消费。
        if (ticket == null || !ticket.matches("[0-9a-f-]{36}")) return null;
        String value = redis.execute(consumeTicket, List.of(namespace + ":ws:" + ticket));
        if (value == null) return null;
        String[] identity = value.split("\\|", 2);
        long id = Long.parseLong(identity[0]);
        // 票据还需绑定未撤销的原登录令牌，退出或停用后不能继续握手。
        Object active = StpUtil.getLoginIdByToken(identity[1]);
        return active != null && identity[0].equals(active.toString()) && users.isActive(id)
                ? id
                : null;
    }

    public String topic() {
        /* 实例使用命名空间隔离通知主题。 */
        return namespace + ":message:changed";
    }

    /** 持久化通知发件箱使用可重试广播，异常交由投递服务处理。 */
    public void broadcast(List<Long> recipients) {
        for (Long id : recipients) redis.convertAndSend(topic(), id.toString());
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession raw) throws Exception {
        long id = (Long) raw.getAttributes().get("userId");
        Map<String, WebSocketSession> active =
                sessions.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        // 每名用户在本实例最多保留五个连接，并限制慢消费者缓冲。
        if (active.size() >= 5) {
            raw.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        active.put(raw.getId(), new ConcurrentWebSocketSessionDecorator(raw, 5000, 65536));
        raw.sendMessage(new TextMessage("{\"type\":\"CONNECTED\"}"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 连接结束必须清理内存引用。
        Long id = (Long) session.getAttributes().get("userId");
        Map<String, WebSocketSession> active = sessions.get(id);
        if (active != null) {
            active.remove(session.getId());
            if (active.isEmpty()) sessions.remove(id, active);
        }
    }

    public void receive(String userId) {
        Map<String, WebSocketSession> active = sessions.get(Long.parseLong(userId));
        if (active == null) return;
        // 仅推送变化信号，不将敏感消息正文广播到 Redis。
        active.forEach(
                (id, session) -> {
                    try {
                        if (session.isOpen())
                            session.sendMessage(new TextMessage("{\"type\":\"MESSAGE_CHANGED\"}"));
                    } catch (IOException ex) {
                        log.warn("消息连接写入失败 session={}", id);
                        try {
                            session.close(CloseStatus.SERVER_ERROR);
                        } catch (IOException close) {
                            ex.addSuppressed(close);
                        }
                    }
                });
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sent(MessageService.Sent event) {
        // 推送失败不撤销已入库消息，客户端重连后通过未读查询恢复。
        for (Long id : event.recipients())
            try {
                redis.convertAndSend(topic(), id.toString());
            } catch (org.springframework.data.redis.RedisConnectionFailureException ex) {
                log.error("消息实时提醒失败，消息已持久化 userId={}", id, ex);
            }
    }
}
