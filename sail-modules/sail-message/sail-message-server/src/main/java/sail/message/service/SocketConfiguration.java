package sail.message.service;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** WebSocket 认证、来源限制和 Redis 实例间消息转发。 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
@RequiredArgsConstructor
public class SocketConfiguration implements WebSocketConfigurer {
    private final MessageSocket socket;

    @Value("${sail.websocket.allowed-origins:http://localhost:5173}")
    private String[] origins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 握手需要一次性票据，并按显式配置校验浏览器来源。
        registry.addHandler(socket, "/ws/messages")
                .setAllowedOrigins(origins)
                .addInterceptors(
                        new HandshakeInterceptor() {
                            @Override
                            public boolean beforeHandshake(
                                    ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler handler,
                                    Map<String, Object> attrs) {
                                String ticket =
                                        UriComponentsBuilder.fromUri(request.getURI())
                                                .build()
                                                .getQueryParams()
                                                .getFirst("ticket");
                                Long id = socket.consume(ticket);
                                if (id == null) {
                                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                                    return false;
                                }
                                attrs.put("userId", id);
                                return true;
                            }

                            @Override
                            public void afterHandshake(
                                    ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler handler,
                                    Exception ex) {
                                /* 无需保留票据或认证秘密。 */
                            }
                        });
    }

    @Bean
    public RedisMessageListenerContainer messageListener(RedisConnectionFactory factory) {
        // 每个应用实例订阅同一主题，再只发送给本机连接。
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(
                (message, pattern) ->
                        socket.receive(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(socket.topic()));
        return container;
    }
}
