package sail.server;

import static org.junit.jupiter.api.Assertions.*;

import com.github.fppt.jedismock.RedisServer;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import sail.framework.security.SecretCipher;

import tools.jackson.databind.*;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** 隔离 HTTP 集成测试：真实 Spring 容器、H2 数据库和测试 Redis 协议服务器。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.context.annotation.Import(FrameworkIntegrationTest.TokenConfiguration.class)
class FrameworkIntegrationTest {
    /** 使用与本地环境一致的命名空间适配器，覆盖搜索键与读取键的一致性。 */
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class TokenConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        sail.server.config.LocalRedisTokenDao tokenDao() {
            // 所有 HTTP 测试仍通过 Redis 协议读写真实会话，不替换业务逻辑。
            return new sail.server.config.LocalRedisTokenDao("sail-integration-test");
        }
    }

    private static final RedisServer REDIS;
    private static final Path STORAGE;

    static {
        // 独立随机端口和临时目录，不访问本机已有数据库或 Redis 数据。
        try {
            REDIS = RedisServer.newRedisServer().start();
            STORAGE = Files.createTempDirectory("sail-storage-test-");
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry r) {
        // 测试密钥与管理员密码仅用于本测试实例。
        r.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:sail;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE");
        r.add("spring.sql.init.mode", () -> "always");
        r.add("spring.sql.init.schema-locations", () -> "classpath:db/base.sql");
        r.add("spring.datasource.username", () -> "sa");
        r.add("spring.datasource.password", () -> "");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", REDIS::getBindPort);
        r.add("spring.data.redis.password", () -> "");
        r.add("sail.storage.local-root", STORAGE::toString);
        r.add("sail.pay.compensation-enabled", () -> false);
        r.add("sail.message.delivery-enabled", () -> false);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired SecretCipher secrets;
    private final HttpClient client = HttpClient.newHttpClient();
    private static String admin, user;
    private static long userId, roleId, fileId, localId;

    private HttpResponse<String> call(String method, String path, String body, String token)
            throws Exception {
        // 所有断言通过真实 HTTP 入口，包含认证拦截、参数校验和数据库调用。
        HttpRequest.Builder b =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return client.send(
                b.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        // 失败时附上响应正文便于定位实际边界问题。
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body()).get("data");
    }

    private String login(String name, String password) throws Exception {
        // 登录经过 BCrypt 与 Redis 会话，未绕过安全框架。
        return data(call(
                        "POST",
                        "/api/system/auth/login",
                        json.writeValueAsString(Map.of("username", name, "password", password)),
                        null))
                .get("tokenValue")
                .asString();
    }

    @Test
    @Order(1)
    void anonymousAndLogin() throws Exception {
        assertEquals(401, call("GET", "/api/system/auth/me", null, null).statusCode());
        admin = login("admin", "SailTestOnly!2026");
        var response = call("GET", "/api/system/auth/me", null, admin);
        assertNotNull(response.headers().firstValue("X-Trace-Id").orElse(null));
        assertEquals("admin", data(response).get("user").get("username").asString());
    }

    @Test
    @Order(2)
    void passwordAndValidationStayPrivate() throws Exception {
        assertEquals(
                401,
                call(
                                "POST",
                                "/api/system/auth/login",
                                "{\"username\":\"admin\",\"password\":\"incorrect\"}",
                                null)
                        .statusCode());
        assertEquals(
                400,
                call("POST", "/api/system/users", "{\"username\":\"short\"}", admin).statusCode());
        String cipher = secrets.encrypt("secret-material");
        assertFalse(cipher.contains("secret-material"));
        assertEquals("secret-material", secrets.decrypt(cipher));
        assertNotEquals(cipher, secrets.encrypt("secret-material"));
    }

    @Test
    @Order(3)
    void rbacAndDataScope() throws Exception {
        userId =
                data(call(
                                "POST",
                                "/api/system/users",
                                "{\"username\":\"worker\",\"password\":\"WorkerTestOnly!2026\",\"nickname\":\"工作人员\",\"status\":1}",
                                admin))
                        .asLong();
        roleId =
                data(call(
                                "POST",
                                "/api/system/roles",
                                "{\"name\":\"普通人员\",\"code\":\"WORKER\",\"status\":1,\"dataScope\":5}",
                                admin))
                        .asLong();
        data(
                call(
                        "PUT",
                        "/api/system/roles/" + roleId + "/menus",
                        "{\"ids\":[1,11,12,13]}",
                        admin));
        data(
                call(
                        "PUT",
                        "/api/system/users/" + userId + "/roles",
                        "{\"ids\":[" + roleId + "]}",
                        admin));
        user = login("worker", "WorkerTestOnly!2026");
        JsonNode users = data(call("GET", "/api/system/users", null, user));
        assertEquals(1, users.get("total").asInt());
        assertEquals(userId, users.get("records").get(0).get("id").asLong());
        assertEquals(403, call("GET", "/api/system/users/1", null, user).statusCode());
        assertEquals(403, call("GET", "/api/pay/channels", null, user).statusCode());
        assertFalse(call("GET", "/api/system/users", null, admin).body().contains("passwordHash"));
    }

    @Test
    @Order(4)
    void messagesBelongToRecipient() throws Exception {
        data(
                call(
                        "POST",
                        "/api/messages/templates",
                        "{\"code\":\"test_notice\",\"name\":\"测试\",\"title\":\"通知"
                                + " ${name}\",\"content\":\"你好 ${name}\",\"enabled\":true}",
                        admin));
        data(
                call(
                        "POST",
                        "/api/messages/send",
                        "{\"templateCode\":\"test_notice\",\"recipientIds\":["
                                + userId
                                + "],\"variables\":{\"name\":\"同事\"}}",
                        admin));
        assertEquals(1, data(call("GET", "/api/messages/unread-count", null, user)).asInt());
        JsonNode page = data(call("GET", "/api/messages/mine", null, user));
        long id = page.get("records").get(0).get("id").asLong();
        data(call("PUT", "/api/messages/read", "{\"ids\":[" + id + "]}", admin));
        assertEquals(1, data(call("GET", "/api/messages/unread-count", null, user)).asInt());
        data(call("PUT", "/api/messages/read-all", null, user));
        assertEquals(0, data(call("GET", "/api/messages/unread-count", null, user)).asInt());
        assertEquals(
                400,
                call(
                                "POST",
                                "/api/messages/send",
                                "{\"templateCode\":\"test_notice\",\"recipientIds\":["
                                        + userId
                                        + "],\"variables\":{}}",
                                admin)
                        .statusCode());
    }

    @Test
    @Order(5)
    void storageSwitchPreservesOldFiles() throws Exception {
        // 上传真实文本文件，随后切换默认存储并确认仍从原配置读取。
        String boundary = "SailBoundary";
        String content =
                "--"
                        + boundary
                        + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\";"
                        + " filename=\"hello.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\n"
                        + "hello sail\r\n"
                        + "--"
                        + boundary
                        + "--\r\n";
        HttpRequest upload =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/file/objects"))
                        .header("Authorization", "Bearer " + user)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(content))
                        .build();
        fileId = data(client.send(upload, HttpResponse.BodyHandlers.ofString())).get("id").asLong();
        localId =
                data(call(
                                "POST",
                                "/api/file/storages",
                                "{\"name\":\"第二本地实例\",\"type\":\"LOCAL\",\"enabled\":true}",
                                admin))
                        .asLong();
        data(call("PUT", "/api/file/storages/" + localId + "/default", null, admin));
        assertEquals(
                "hello sail",
                call("GET", "/api/file/objects/" + fileId + "/download", null, user).body());
        assertEquals(404, call("GET", "/api/file/public/" + fileId, null, null).statusCode());
        assertEquals(400, call("DELETE", "/api/file/storages/1", null, admin).statusCode());
        JsonNode grouped = data(call("GET", "/api/file/storages", null, admin));
        assertEquals(2, grouped.get("LOCAL").size());
        assertTrue(grouped.has("MINIO"));
        assertTrue(grouped.has("ALIYUN"));
    }

    @Test
    @Order(6)
    void storageSecretsAndPathSafety() throws Exception {
        data(
                call(
                        "POST",
                        "/api/file/storages",
                        "{\"name\":\"仅测试配置保存\",\"type\":\"MINIO\",\"endpoint\":\"http://localhost:19000\",\"bucket\":\"test-bucket\",\"region\":\"us-east-1\",\"accessKey\":\"test-access\",\"secretKey\":\"test-secret\",\"enabled\":false}",
                        admin));
        String body = call("GET", "/api/file/storages", null, admin).body();
        assertFalse(body.contains("test-secret"));
        assertFalse(body.contains("test-access"));
        var local = new sail.framework.oss.LocalObjectStore(STORAGE.toString());
        var spec =
                new sail.framework.oss.StorageSpec(
                        99, sail.framework.oss.StorageType.LOCAL, null, null, null, null, null);
        assertThrows(
                IllegalArgumentException.class, () -> local.download(spec, "../../outside.txt"));
    }

    @Test
    @Order(7)
    void paymentNeedsActualMerchantConfiguration() throws Exception {
        long app =
                data(call(
                                "POST",
                                "/api/pay/applications",
                                "{\"code\":\"integration\",\"name\":\"测试业务\",\"enabled\":true}",
                                admin))
                        .asLong();
        assertEquals(
                400,
                call(
                                "POST",
                                "/api/pay/channels",
                                "{\"applicationId\":"
                                        + app
                                        + ",\"type\":\"WECHAT_NATIVE\",\"name\":\"微信\",\"appId\":\"wx-test\",\"notifyBaseUrl\":\"https://example.com\",\"enabled\":true}",
                                admin)
                        .statusCode());
        assertEquals(
                400,
                call(
                                "POST",
                                "/api/pay/orders",
                                "{\"applicationId\":"
                                        + app
                                        + ",\"channelId\":1,\"businessNo\":\"B1\",\"subject\":\"订单\",\"amount\":-1,\"expiresMinutes\":10}",
                                admin)
                        .statusCode());
    }

    @Test
    @Order(8)
    void roleChangesTakeEffectAndDisableRevokesSession() throws Exception {
        data(call("PUT", "/api/system/roles/" + roleId + "/menus", "{\"ids\":[]}", admin));
        assertEquals(403, call("GET", "/api/system/users", null, user).statusCode());
        data(
                call(
                        "PUT",
                        "/api/system/users/" + userId,
                        "{\"nickname\":\"已停用\",\"status\":0}",
                        admin));
        assertEquals(401, call("GET", "/api/system/auth/me", null, user).statusCode());
    }

    @Autowired sail.pay.service.PayStateService states;
    @Autowired sail.pay.service.PayConfigurationService payConfig;
    @Autowired sail.pay.service.AlipayGateway alipay;
    @Autowired sail.pay.service.WechatGateway wechat;
    @Autowired sail.message.service.MessageSocket sockets;

    @Test
    @Order(9)
    void websocketTicketIsOneTimeAndCanConnect() throws Exception {
        // 真实 HTTP 领取票据，通过标准客户端进行 WebSocket 握手。
        String ticket =
                data(call("POST", "/api/messages/socket-ticket", null, admin))
                        .get("ticket")
                        .asString();
        java.util.concurrent.CompletableFuture<String> received =
                new java.util.concurrent.CompletableFuture<>();
        WebSocket ws =
                client.newWebSocketBuilder()
                        .buildAsync(
                                URI.create(
                                        "ws://127.0.0.1:" + port + "/ws/messages?ticket=" + ticket),
                                new WebSocket.Listener() {
                                    @Override
                                    public java.util.concurrent.CompletionStage<?> onText(
                                            WebSocket webSocket, CharSequence text, boolean last) {
                                        received.complete(text.toString());
                                        webSocket.request(1);
                                        return null;
                                    }
                                })
                        .join();
        assertTrue(received.get(5, java.util.concurrent.TimeUnit.SECONDS).contains("CONNECTED"));
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        assertNull(sockets.consume(ticket));
    }

    @Test
    @Order(10)
    void paymentFactsAreValidatedAndIdempotent() throws Exception {
        // 插入隔离交易事实，测试状态服务而不调用真实商户网络。
        long app =
                data(call(
                                "POST",
                                "/api/pay/applications",
                                "{\"code\":\"facts\",\"name\":\"状态验证\",\"enabled\":true}",
                                admin))
                        .asLong();
        db.update(
                "INSERT INTO pay_channel(application_id,type,name,app_id,merchant_id,enabled)"
                        + " VALUES (?,?,?,?,?,TRUE)",
                app,
                "WECHAT_NATIVE",
                "验证渠道",
                "wx-facts",
                "merchant-facts");
        long channel =
                db.queryForObject("SELECT id FROM pay_channel WHERE app_id='wx-facts'", Long.class);
        db.update(
                "INSERT INTO"
                    + " pay_order(application_id,channel_id,owner_id,business_no,order_no,subject,amount,currency,status,expires_at)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                app,
                channel,
                1,
                "BUSINESS-FACT",
                "PAY-FACT",
                "验证订单",
                1234,
                "CNY",
                "PENDING");
        // 从数据库加载实体后调用真实原子状态推进逻辑。
        long id =
                db.queryForObject("SELECT id FROM pay_order WHERE order_no='PAY-FACT'", Long.class);
        var order = new sail.pay.entity.PayOrder();
        order.setId(id);
        order.setApplicationId(app);
        order.setChannelId(channel);
        order.setBusinessNo("BUSINESS-FACT");
        order.setOrderNo("PAY-FACT");
        order.setAmount(1234L);
        order.setCurrency("CNY");
        var c = payConfig.get(channel);
        assertThrows(
                sail.common.core.BusinessException.class,
                () ->
                        states.trade(
                                order,
                                c,
                                new sail.pay.service.ChannelGateway.Trade(
                                        "PAY-FACT",
                                        "WX1",
                                        "wx-facts",
                                        "merchant-facts",
                                        1235,
                                        "CNY",
                                        "SUCCESS")));
        assertEquals(
                "PENDING",
                db.queryForObject("SELECT status FROM pay_order WHERE id=?", String.class, id));
        var fact =
                new sail.pay.service.ChannelGateway.Trade(
                        "PAY-FACT", "WX1", "wx-facts", "merchant-facts", 1234, "CNY", "SUCCESS");
        states.trade(order, c, fact);
        states.trade(order, c, fact);
        assertEquals(
                "PAID",
                db.queryForObject("SELECT status FROM pay_order WHERE id=?", String.class, id));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM pay_notification WHERE order_id=?",
                        Integer.class,
                        id));
        states.trade(
                order,
                c,
                new sail.pay.service.ChannelGateway.Trade(
                        "PAY-FACT", "WX1", "wx-facts", "merchant-facts", 1234, "CNY", "CLOSED"));
        assertEquals(
                "PAID",
                db.queryForObject("SELECT status FROM pay_order WHERE id=?", String.class, id));
    }

    @Test
    @Order(11)
    void alipaySignatureCannotBeTamperedWith() throws Exception {
        // 使用测试生成 RSA 密钥，实际执行支付宝官方 SDK 的签名与验签。
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        var c = new sail.pay.entity.PayChannel();
        c.setAppId("test-app");
        c.setMerchantId("test-seller");
        c.setPublicKeyCipher(secrets.encrypt(publicKey));
        Map<String, String> params =
                new HashMap<>(
                        Map.of(
                                "app_id",
                                "test-app",
                                "seller_id",
                                "test-seller",
                                "out_trade_no",
                                "TEST1",
                                "total_amount",
                                "12.34",
                                "trade_status",
                                "TRADE_SUCCESS",
                                "sign_type",
                                "RSA2"));
        params.put(
                "sign",
                com.alipay.api.internal.util.AlipaySignature.rsaSign(
                        com.alipay.api.internal.util.AlipaySignature.getSignCheckContentV1(params),
                        privateKey,
                        "UTF-8",
                        "RSA2"));
        assertEquals("TEST1", alipay.verify(c, params));
        params.put("total_amount", "99.99");
        assertThrows(sail.common.core.BusinessException.class, () -> alipay.verify(c, params));
    }

    @Test
    @Order(12)
    void wechatNotificationIsSignedAndEncrypted() throws Exception {
        // 平台公钥与商户私钥使用独立测试密钥，按 v3 真实消息格式加密并签名。
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var platform = generator.generateKeyPair();
        var merchant = generator.generateKeyPair();
        String platformPublic =
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getEncoder().encodeToString(platform.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----";
        String merchantPrivate =
                "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getEncoder().encodeToString(merchant.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----";
        var c = new sail.pay.entity.PayChannel();
        c.setAppId("wx-test");
        c.setMerchantId("merchant-test");
        c.setMerchantSerial("SERIAL_TEST");
        c.setPublicKeyId("PUB_KEY_ID_TEST");
        c.setPrivateKeyCipher(secrets.encrypt(merchantPrivate));
        c.setPublicKeyCipher(secrets.encrypt(platformPublic));
        String apiKey = "12345678901234567890123456789012";
        c.setApiV3KeyCipher(secrets.encrypt(apiKey));
        String resourceNonce = "123456789012", associated = "transaction";
        String transaction =
                json.writeValueAsString(
                        Map.of(
                                "appid",
                                "wx-test",
                                "mchid",
                                "merchant-test",
                                "out_trade_no",
                                "TEST-WX",
                                "transaction_id",
                                "WXTX",
                                "trade_state",
                                "SUCCESS",
                                "amount",
                                Map.of("total", 1234, "currency", "CNY")));
        var aes = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "AES"),
                new javax.crypto.spec.GCMParameterSpec(
                        128, resourceNonce.getBytes(StandardCharsets.UTF_8)));
        aes.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
        String body =
                json.writeValueAsString(
                        Map.of(
                                "id",
                                "notify-test",
                                "event_type",
                                "TRANSACTION.SUCCESS",
                                "resource_type",
                                "encrypt-resource",
                                "resource",
                                Map.of(
                                        "algorithm",
                                        "AEAD_AES_256_GCM",
                                        "nonce",
                                        resourceNonce,
                                        "associated_data",
                                        associated,
                                        "ciphertext",
                                        Base64.getEncoder()
                                                .encodeToString(
                                                        aes.doFinal(
                                                                transaction.getBytes(
                                                                        StandardCharsets
                                                                                .UTF_8))))));
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond()),
                nonce = "test-nonce";
        var signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(platform.getPrivate());
        signature.update(
                (timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Wechatpay-Timestamp", timestamp);
        request.addHeader("Wechatpay-Serial", "PUB_KEY_ID_TEST");
        request.addHeader("Wechatpay-Nonce", nonce);
        request.addHeader(
                "Wechatpay-Signature", Base64.getEncoder().encodeToString(signature.sign()));
        assertEquals("TEST-WX", wechat.verify(c, request, body));
        assertThrows(RuntimeException.class, () -> wechat.verify(c, request, body + " "));
    }

    @Test
    @Order(13)
    void directoryQueriesFilterSqlAndRetainAncestorPaths() throws Exception {
        // 使用隔离测试库的真实 HTTP 接口，验证服务端响应已经排除不匹配记录。
        String session = login("admin", "SailTestOnly!2026");
        long parent =
                data(call(
                                "POST",
                                "/api/system/depts",
                                "{\"name\":\"FILTER_PARENT\",\"sortOrder\":0,\"status\":1}",
                                session))
                        .asLong();
        long child =
                data(call(
                                "POST",
                                "/api/system/depts",
                                "{\"name\":\"FILTER_CHILD\",\"parentId\":"
                                        + parent
                                        + ",\"sortOrder\":1,\"status\":0}",
                                session))
                        .asLong();
        var branch = data(call("GET", "/api/system/depts?keyword=FILTER_CHILD", null, session));
        assertEquals(2, branch.size());
        assertEquals(
                Set.of(parent, child),
                Set.of(branch.get(0).get("id").asLong(), branch.get(1).get("id").asLong()));
        assertEquals(
                0,
                data(call("GET", "/api/system/depts?keyword=FILTER_CHILD&status=1", null, session))
                        .size());
        assertEquals(
                0,
                data(call(
                                "GET",
                                "/api/system/depts?keyword=FILTER_CHILD&createdTo=2000-01-01T00:00:00",
                                null,
                                session))
                        .size());
        // 精确编号允许带上真实父级上下文；不应返回其他分支或下级记录。
        assertEquals(1, data(call("GET", "/api/system/depts?id=" + parent, null, session)).size());
        long role =
                data(call(
                                "POST",
                                "/api/system/roles",
                                "{\"name\":\"FILTER_ROLE\",\"code\":\"FILTER_ROLE\",\"status\":0,\"dataScope\":5,\"remark\":\"FILTER_REMARK\"}",
                                session))
                        .asLong();
        assertEquals(
                1,
                data(call(
                                "GET",
                                "/api/system/roles?remark=FILTER_REMARK&id=" + role,
                                null,
                                session))
                        .size());
        assertEquals(
                0,
                data(call("GET", "/api/system/roles?remark=ABSENT_REMARK", null, session)).size());
        assertEquals(400, call("GET", "/api/system/roles?id=0", null, session).statusCode());
        assertEquals(
                400,
                call(
                                "GET",
                                "/api/system/depts?createdFrom=2026-11-02T00:00:00&createdTo=2026-11-01T00:00:00",
                                null,
                                session)
                        .statusCode());
        // 验收数据按引用顺序清理，确认逻辑删除后不会再被查询返回。
        data(call("DELETE", "/api/system/depts/" + child, null, session));
        data(call("DELETE", "/api/system/depts/" + parent, null, session));
        data(call("DELETE", "/api/system/roles/" + role, null, session));
        assertEquals(
                0, data(call("GET", "/api/system/depts?keyword=FILTER_", null, session)).size());
    }

    @Test
    @Order(14)
    void workbenchUsesRealAggregatesAndProtectsSessions() throws Exception {
        // 真实 HTTP 验证匿名及普通账号不能读取管理员会话或审计统计。
        String session = login("admin", "SailTestOnly!2026");
        assertEquals(401, call("GET", "/api/system/workbench/online", null, null).statusCode());
        data(
                call(
                        "POST",
                        "/api/system/users",
                        "{\"username\":\"workbench_reader\",\"password\":\"ReaderTestOnly!2026\",\"nickname\":\"工作台普通账号\",\"status\":1}",
                        session));
        String reader = login("workbench_reader", "ReaderTestOnly!2026");
        assertEquals(403, call("GET", "/api/system/workbench/online", null, reader).statusCode());
        assertEquals(403, call("GET", "/api/system/workbench/audit", null, reader).statusCode());
        var response = call("GET", "/api/system/workbench/online", null, session);
        var online = data(response);
        assertEquals(2, online.get("total").asInt());
        assertTrue(online.get("sessionTotal").asInt() >= 2);
        assertTrue(online.get("records").toString().contains("admin"));
        assertFalse(response.body().contains(session));
        assertFalse(response.body().contains("tokenValue"));
        assertFalse(response.body().contains("password"));
        // 退出的终端会立刻从在线账号中排除，不能把残留账号会话算作在线。
        data(call("POST", "/api/system/auth/logout", null, reader));
        assertEquals(
                1,
                data(call("GET", "/api/system/workbench/online", null, session))
                        .get("total")
                        .asInt());

        var before = data(call("GET", "/api/system/workbench/audit", null, session));
        var midnight = java.time.LocalDate.now().atStartOfDay();
        // 包含窗口首日零点，排除窗口外和逻辑删除记录；按数据库真实时间分组。
        for (int i = 0; i < 5; i++) {
            var date =
                    i == 0
                            ? midnight.minusDays(6)
                            : i == 1 ? midnight.minusDays(7) : midnight.plusSeconds(1);
            db.update(
                    "INSERT INTO sys_audit(user_id, action, success, trace_id, deleted, created_at,"
                            + " updated_at) VALUES(1, ?, ?, 'workbench-test', ?, ?, ?)",
                    i == 3 ? "登录" : "工作台统计验收",
                    i != 3,
                    i == 4 ? 999 : 0,
                    date,
                    date);
        }
        var after = data(call("GET", "/api/system/workbench/audit", null, session));
        assertEquals(7, after.get("days").size());
        assertEquals(
                midnight.minusDays(6).toLocalDate().toString(),
                after.get("days").get(0).get("date").asString());
        assertEquals(
                before.get("days").get(0).get("success").asLong() + 1,
                after.get("days").get(0).get("success").asLong());
        assertEquals(
                before.get("days").get(6).get("success").asLong() + 1,
                after.get("days").get(6).get("success").asLong());
        assertEquals(
                before.get("days").get(6).get("failed").asLong() + 1,
                after.get("days").get(6).get("failed").asLong());
        assertEquals(
                before.get("days").get(6).get("loginFailures").asLong() + 1,
                after.get("days").get(6).get("loginFailures").asLong());
        assertTrue(after.get("recent").size() <= 8);
        assertTrue(after.get("recent").toString().contains("admin"));
    }

    @Test
    @Order(15)
    void auditContainsActualFailureInputsResultTraceAndDuration() throws Exception {
        String session = login("admin", "SailTestOnly!2026");
        var logger =
                (ch.qos.logback.classic.Logger)
                        org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var captured =
                new ch.qos.logback.core.read.ListAppender<
                        ch.qos.logback.classic.spi.ILoggingEvent>();
        captured.start();
        logger.addAppender(captured);
        try {
            // 真实参数校验失败也应进入控制台及持久审计，拒绝值中的密码不能泄漏。
            var failure =
                    call(
                            "POST",
                            "/api/system/users",
                            "{\"username\":\"audit_check\",\"password\":\"PrivateInput2026\",\"nickname\":\"\",\"status\":1}",
                            session);
            assertEquals(400, failure.statusCode(), failure.body());
            String trace = json.readTree(failure.body()).get("traceId").asString();
            assertEquals(trace, failure.headers().firstValue("X-Trace-Id").orElseThrow());
            assertTrue(
                    json.readTree(failure.body()).get("message").asString().contains("nickname"));
            var audit =
                    data(call(
                                    "GET",
                                    "/api/system/audit?keyword="
                                            + trace
                                            + "&requestMethod=POST&operationType=INSERT",
                                    null,
                                    session))
                            .get("records")
                            .get(0);
            assertEquals("INSERT", audit.get("operationType").asString());
            assertEquals("POST", audit.get("requestMethod").asString());
            assertEquals("/api/system/users", audit.get("requestUrl").asString());
            assertFalse(audit.get("success").asBoolean());
            assertEquals(400, audit.get("httpStatus").asInt());
            assertEquals(400, audit.get("responseCode").asInt());
            assertTrue(audit.get("durationMs").asLong() >= 0);
            assertTrue(audit.get("requestBody").asString().contains("[已脱敏]"));
            assertFalse(audit.toString().contains("PrivateInput2026"));
            assertTrue(audit.get("responseBody").asString().contains(trace));
            assertTrue(audit.get("errorMessage").asString().contains("nickname"));
            assertTrue(
                    audit.get("createdAt")
                            .asString()
                            .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
            assertEquals(
                    0,
                    data(call(
                                    "GET",
                                    "/api/system/audit?keyword=" + trace + "&requestMethod=DELETE",
                                    null,
                                    session))
                            .get("total")
                            .asInt());
            String output =
                    captured.list.stream()
                            .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                            .collect(java.util.stream.Collectors.joining("\n"));
            assertTrue(output.contains("参数校验失败 traceId=" + trace));
            assertTrue(output.contains("请求完成 traceId=" + trace));
            assertTrue(output.contains("durationMs="));
            assertTrue(output.contains("requestBody="));
            assertTrue(output.contains("responseBody="));
            assertFalse(output.contains("PrivateInput2026"));
            // 登录响应会返回令牌给客户端，日志和审计只保留脱敏快照。
            var loggedIn =
                    call(
                            "POST",
                            "/api/system/auth/login",
                            "{\"username\":\"admin\",\"password\":\"SailTestOnly!2026\"}",
                            null);
            var loginTrace = json.readTree(loggedIn.body()).get("traceId").asString();
            var loginAudit =
                    data(call("GET", "/api/system/audit?keyword=" + loginTrace, null, session))
                            .get("records")
                            .get(0);
            assertEquals("LOGIN", loginAudit.get("operationType").asString());
            assertTrue(loginAudit.get("success").asBoolean());
            assertFalse(
                    loginAudit
                            .get("responseBody")
                            .asString()
                            .contains(data(loggedIn).get("tokenValue").asString()));
            assertFalse(loginAudit.get("requestBody").asString().contains("SailTestOnly!2026"));
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    @Order(16)
    void dictionaryTypeDataAndOptionsShareActualConfiguration() throws Exception {
        String session = login("admin", "SailTestOnly!2026");
        long type =
                data(call(
                                "POST",
                                "/api/system/dictionaries/types",
                                "{\"code\":\"qa_flow\",\"name\":\"流程状态\",\"status\":1}",
                                session))
                        .asLong();
        long item =
                data(call(
                                "POST",
                                "/api/system/dictionaries",
                                "{\"typeCode\":\"qa_flow\",\"label\":\"已确认\",\"value\":\"CONFIRMED\",\"sortOrder\":1,\"status\":1,\"color\":\"success\",\"isDefault\":true}",
                                session))
                        .asLong();
        // 类型列表和数据列表都按 SQL 条件过滤；类型更新同步迁移数据。
        assertEquals(
                1,
                data(call("GET", "/api/system/dictionaries/types?keyword=qa_flow", null, session))
                        .size());
        var options = data(call("GET", "/api/system/dictionaries/options", null, session));
        assertEquals("已确认", options.get("qa_flow").get(0).get("label").asString());
        assertTrue(options.get("qa_flow").get(0).get("isDefault").asBoolean());
        data(
                call(
                        "PUT",
                        "/api/system/dictionaries/types/" + type,
                        "{\"code\":\"qa_flow_new\",\"name\":\"流程状态\",\"status\":1}",
                        session));
        assertEquals(
                0,
                data(call("GET", "/api/system/dictionaries?typeCode=qa_flow", null, session))
                        .size());
        assertEquals(
                1,
                data(call("GET", "/api/system/dictionaries?typeCode=qa_flow_new", null, session))
                        .size());
        assertEquals(
                400,
                call("DELETE", "/api/system/dictionaries/types/" + type, null, session)
                        .statusCode());
        long builtin =
                db.queryForObject(
                        "SELECT id FROM sys_dictionary_type WHERE code='sys_operation_type' AND"
                                + " deleted=0",
                        Long.class);
        assertEquals(
                400,
                call("DELETE", "/api/system/dictionaries/types/" + builtin, null, session)
                        .statusCode());
        data(
                call(
                        "PUT",
                        "/api/system/dictionaries/" + item,
                        "{\"typeCode\":\"qa_flow_new\",\"label\":\"暂停\",\"value\":\"CONFIRMED\",\"sortOrder\":1,\"status\":0,\"color\":\"warning\",\"isDefault\":false}",
                        session));
        assertEquals(
                0,
                data(call("GET", "/api/system/dictionaries/options", null, session))
                        .get("qa_flow_new")
                        .size());
        data(call("DELETE", "/api/system/dictionaries/" + item, null, session));
        data(call("DELETE", "/api/system/dictionaries/types/" + type, null, session));
        assertEquals(
                type,
                db.queryForObject(
                        "SELECT deleted FROM sys_dictionary_type WHERE id=?", Long.class, type));
    }

    @Test
    @Order(17)
    void groupedSettingsValidateAtomicallyAndMenuIconRoundTrips() throws Exception {
        String session = login("admin", "SailTestOnly!2026");
        String previous =
                db.queryForObject(
                        "SELECT config_value FROM sys_config WHERE config_key='website.title' AND"
                                + " deleted=0",
                        String.class);
        // 超大数字返回明确的业务错误，同组合法值不能被部分提交。
        assertEquals(
                400,
                call(
                                "PUT",
                                "/api/system/configs/settings",
                                "{\"website.title\":\"不应保存\",\"login.max_attempts\":\"999999999999999999999\"}",
                                session)
                        .statusCode());
        assertEquals(
                previous,
                db.queryForObject(
                        "SELECT config_value FROM sys_config WHERE config_key='website.title' AND"
                                + " deleted=0",
                        String.class));
        data(
                call(
                        "PUT",
                        "/api/system/configs/settings",
                        "{\"website.title\":\"配置验收\",\"website.logo\":\"/logo.svg\"}",
                        session));
        var website = data(call("GET", "/api/system/configs/website", null, session));
        assertEquals("配置验收", website.get("website.title").asString());
        assertEquals(2, website.size());
        assertFalse(website.has("login.max_attempts"));
        assertTrue(
                data(call("GET", "/api/system/configs/settings", null, session))
                        .toString()
                        .contains("配置验收"));
        long menu =
                data(call(
                                "POST",
                                "/api/system/menus",
                                "{\"name\":\"图标验收\",\"path\":\"/qa-icon\",\"type\":\"MENU\",\"icon\":\"book-open\",\"sortOrder\":1,\"status\":1}",
                                session))
                        .asLong();
        var saved = data(call("GET", "/api/system/menus?keyword=qa-icon", null, session)).get(0);
        assertEquals("book-open", saved.get("icon").asString());
        assertTrue(
                data(call("GET", "/api/system/auth/menus", null, session))
                        .toString()
                        .contains("book-open"));
        data(
                call(
                        "PUT",
                        "/api/system/menus/" + menu,
                        "{\"name\":\"图标验收\",\"path\":\"/qa-icon\",\"type\":\"MENU\",\"icon\":\"\",\"sortOrder\":1,\"status\":1}",
                        session));
        assertEquals(
                "",
                data(call("GET", "/api/system/menus?keyword=qa-icon", null, session))
                        .get(0)
                        .get("icon")
                        .asString());
        data(call("DELETE", "/api/system/menus/" + menu, null, session));
        data(
                call(
                        "PUT",
                        "/api/system/configs/settings",
                        json.writeValueAsString(
                                Map.of("website.title", previous, "website.logo", "")),
                        session));
    }

    @Test
    @Order(18)
    void logSanitizationRejectsMalformedBodiesAndRedactsNestedTickets() {
        // 脱敏先于截断，对数组和嵌套对象同样生效，残缺 JSON 不输出原始正文。
        var sanitizer = new sail.framework.web.LogSanitizer(json);
        var safe =
                sanitizer.json(
                        "{\"items\":[{\"password\":\"raw-secret\",\"ticket\":\"ws-ticket\",\"name\":\"正常值\"}]}");
        assertTrue(safe.contains("正常值"));
        assertFalse(safe.contains("raw-secret"));
        assertFalse(safe.contains("ws-ticket"));
        assertFalse(sanitizer.json("{\"password\":\"partial-secret").contains("partial-secret"));
        assertFalse(sanitizer.json("\"scalar-secret\"").contains("scalar-secret"));
        assertTrue(
                sanitizer
                        .json("{\"name\":\"" + "a".repeat(9000) + "\",\"password\":\"raw-secret\"}")
                        .endsWith("[已截断]"));
    }

    @Test
    @Order(19)
    void operationTypesFollowBusinessActionsInsteadOfOnlyHttpMethods() throws Exception {
        String session = login("admin", "SailTestOnly!2026");
        // 仅操作隔离数据库中不存在的编号，不调用真实支付渠道或撤销现有会话。
        var paths =
                new String[][] {
                    {"DELETE", "/api/system/audit/sessions/999999", "FORCE_LOGOUT"},
                    {"POST", "/api/pay/orders/999999/close", "UPDATE"},
                    {"POST", "/api/pay/orders/999999/sync", "QUERY"}
                };
        for (var action : paths) {
            var response = call(action[0], action[1], null, session);
            var trace = json.readTree(response.body()).get("traceId").asString();
            var audit =
                    data(call("GET", "/api/system/audit?keyword=" + trace, null, session))
                            .get("records")
                            .get(0);
            assertEquals(action[2], audit.get("operationType").asString());
            assertEquals(action[0], audit.get("requestMethod").asString());
        }
    }

    @Autowired sail.message.service.MessageDeliveryService delivery;
    private static long messageUserA,
            messageUserB,
            messageUserC,
            messageDept,
            messageChild,
            messageRole,
            messagePost,
            messageTask,
            messageAttachment;
    private static String messageAdmin, messageTokenA, messageTokenB;
    private static JsonNode messagePreview;

    /** 使用真实管理接口创建独立人员，避免测试消息干扰其他测试账号。 */
    private long messageUser(String username, long dept) throws Exception {
        return data(call(
                        "POST",
                        "/api/system/users",
                        json.writeValueAsString(
                                Map.of(
                                        "username",
                                        username,
                                        "password",
                                        "123456",
                                        "nickname",
                                        username,
                                        "deptId",
                                        dept,
                                        "status",
                                        1)),
                        messageAdmin))
                .asLong();
    }

    /** 草稿构造只提供明确测试数据，实际解析和投递仍运行生产实现。 */
    private Map<String, Object> messageDraft(List<Long> recipients) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("requestKey", UUID.randomUUID().toString());
        draft.put("version", 0);
        draft.put("title", "消息中心验收");
        draft.put(
                "content",
                "<h2>发布内容</h2><p>消息正文<script>alert(1)</script><a"
                        + " href=\"javascript:alert(1)\">安全链接</a></p>");
        draft.put("category", "NOTICE");
        draft.put("priority", "IMPORTANT");
        draft.put("requireConfirmation", true);
        draft.put("pinned", true);
        draft.put(
                "audience",
                Map.of(
                        "userIds",
                        recipients,
                        "deptIds",
                        List.of(),
                        "roleIds",
                        List.of(),
                        "postIds",
                        List.of(),
                        "excludedIds",
                        List.of(),
                        "includeChildren",
                        true,
                        "all",
                        false));
        draft.put("attachmentIds", List.of());
        return draft;
    }

    /** 发布与预览分开调用，验证真实指纹契约。 */
    private long publishMessage(Map<String, Object> draft) throws Exception {
        long id =
                data(call(
                                "POST",
                                "/api/messages/tasks",
                                json.writeValueAsString(draft),
                                messageAdmin))
                        .get("id")
                        .asLong();
        var preview =
                data(call("POST", "/api/messages/tasks/" + id + "/preview", null, messageAdmin));
        data(
                call(
                        "POST",
                        "/api/messages/tasks/" + id + "/publish",
                        json.writeValueAsString(
                                Map.of(
                                        "version",
                                        preview.get("version").asInt(),
                                        "fingerprint",
                                        preview.get("fingerprint").asString())),
                        messageAdmin));
        return id;
    }

    @Test
    @Order(20)
    void messageAudienceUnionSnapshotAndDraftConcurrency() throws Exception {
        messageAdmin = login("admin", "SailTestOnly!2026");
        // 覆盖用户、部门下级、角色、岗位并集与排除，不依赖前端传最终人员清单。
        messageDept =
                data(call(
                                "POST",
                                "/api/system/depts",
                                "{\"name\":\"消息验收部门\",\"sortOrder\":10,\"status\":1}",
                                messageAdmin))
                        .asLong();
        messageChild =
                data(call(
                                "POST",
                                "/api/system/depts",
                                json.writeValueAsString(
                                        Map.of(
                                                "name",
                                                "消息验收子部门",
                                                "parentId",
                                                messageDept,
                                                "sortOrder",
                                                11,
                                                "status",
                                                1)),
                                messageAdmin))
                        .asLong();
        messageUserA = messageUser("message_a", messageChild);
        messageUserB = messageUser("message_b", messageChild);
        messageUserC = messageUser("message_c", messageDept);
        messageTokenA = login("message_a", "123456");
        messageTokenB = login("message_b", "123456");
        messageRole =
                data(call(
                                "POST",
                                "/api/system/roles",
                                "{\"name\":\"消息接收范围\",\"code\":\"MESSAGE_AUDIENCE\",\"status\":1,\"dataScope\":5}",
                                messageAdmin))
                        .asLong();
        messagePost =
                data(call(
                                "POST",
                                "/api/system/posts",
                                "{\"name\":\"消息验收岗位\",\"code\":\"MESSAGE_POST\",\"status\":1}",
                                messageAdmin))
                        .asLong();
        for (long id : List.of(messageUserB, messageUserC))
            data(
                    call(
                            "PUT",
                            "/api/system/users/" + id + "/roles",
                            json.writeValueAsString(Map.of("ids", List.of(messageRole))),
                            messageAdmin));
        data(
                call(
                        "PUT",
                        "/api/system/users/" + messageUserC + "/posts",
                        json.writeValueAsString(Map.of("ids", List.of(messagePost))),
                        messageAdmin));
        Map<String, Object> draft = messageDraft(List.of(messageUserA));
        draft.put(
                "audience",
                Map.of(
                        "userIds",
                        List.of(messageUserA),
                        "deptIds",
                        List.of(messageDept),
                        "roleIds",
                        List.of(messageRole),
                        "postIds",
                        List.of(messagePost),
                        "excludedIds",
                        List.of(messageUserC),
                        "includeChildren",
                        true,
                        "all",
                        false));
        var saved =
                data(
                        call(
                                "POST",
                                "/api/messages/tasks",
                                json.writeValueAsString(draft),
                                messageAdmin));
        messageTask = saved.get("id").asLong();
        assertFalse(saved.get("content").asString().contains("<script"));
        assertFalse(saved.get("content").asString().contains("javascript:"));
        assertEquals(
                messageTask,
                data(call(
                                "POST",
                                "/api/messages/tasks",
                                json.writeValueAsString(draft),
                                messageAdmin))
                        .get("id")
                        .asLong());
        data(
                call(
                        "PUT",
                        "/api/messages/tasks/" + messageTask,
                        json.writeValueAsString(draft),
                        messageAdmin));
        assertEquals(
                409,
                call(
                                "PUT",
                                "/api/messages/tasks/" + messageTask,
                                json.writeValueAsString(draft),
                                messageAdmin)
                        .statusCode());
        messagePreview =
                data(
                        call(
                                "POST",
                                "/api/messages/tasks/" + messageTask + "/preview",
                                null,
                                messageAdmin));
        assertEquals(2, messagePreview.get("count").asInt());
        // 人员快照改变后旧确认指纹必须失效。
        db.update("UPDATE sys_user SET nickname='更新后的接收人' WHERE id=?", messageUserA);
        assertEquals(
                409,
                call(
                                "POST",
                                "/api/messages/tasks/" + messageTask + "/publish",
                                json.writeValueAsString(
                                        Map.of(
                                                "version",
                                                messagePreview.get("version").asInt(),
                                                "fingerprint",
                                                messagePreview.get("fingerprint").asString())),
                                messageAdmin)
                        .statusCode());
        messagePreview =
                data(
                        call(
                                "POST",
                                "/api/messages/tasks/" + messageTask + "/preview",
                                null,
                                messageAdmin));
        var publishBody =
                json.writeValueAsString(
                        Map.of(
                                "version",
                                messagePreview.get("version").asInt(),
                                "fingerprint",
                                messagePreview.get("fingerprint").asString()));
        data(
                call(
                        "POST",
                        "/api/messages/tasks/" + messageTask + "/publish",
                        publishBody,
                        messageAdmin));
        data(
                call(
                        "POST",
                        "/api/messages/tasks/" + messageTask + "/publish",
                        publishBody,
                        messageAdmin));
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM message_recipient WHERE task_id=?",
                        Integer.class,
                        messageTask));
        db.update("UPDATE sys_user SET dept_id=NULL WHERE id=?", messageUserB);
        delivery.deliverDue();
        delivery.deliverDue();
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM message_record WHERE task_id=?",
                        Integer.class,
                        messageTask));
        assertEquals(
                "SENT",
                data(call("GET", "/api/messages/tasks/" + messageTask, null, messageAdmin))
                        .get("status")
                        .asString());
    }

    @Test
    @Order(21)
    void messageReadingConfirmationArchiveAndServerFilters() throws Exception {
        // 角色分配会正常撤销原会话，重新登录后再验证收件归属。
        messageTokenB = login("message_b", "123456");
        var page =
                data(
                        call(
                                "GET",
                                "/api/messages/mine?folder=unread&category=NOTICE&priority=IMPORTANT&keyword="
                                        + java.net.URLEncoder.encode(
                                                "消息中心验收", StandardCharsets.UTF_8),
                                null,
                                messageTokenA));
        assertEquals(1, page.get("total").asInt());
        long id = page.get("records").get(0).get("id").asLong();
        assertEquals(
                404, call("GET", "/api/messages/mine/" + id, null, messageTokenB).statusCode());
        var detail = data(call("GET", "/api/messages/mine/" + id, null, messageTokenA));
        assertTrue(detail.get("readFlag").asBoolean());
        assertTrue(detail.get("confirmedAt").isNull());
        String firstRead = detail.get("readAt").asString();
        assertFalse(firstRead.contains("T"));
        assertEquals(
                firstRead,
                data(call("GET", "/api/messages/mine/" + id, null, messageTokenA))
                        .get("readAt")
                        .asString());
        data(call("PUT", "/api/messages/mine/" + id + "/confirm", null, messageTokenA));
        assertFalse(
                data(call("GET", "/api/messages/mine/" + id, null, messageTokenA))
                        .get("confirmedAt")
                        .isNull());
        data(
                call(
                        "PUT",
                        "/api/messages/archive",
                        json.writeValueAsString(Map.of("ids", List.of(id), "archived", true)),
                        messageTokenA));
        assertEquals(
                0,
                data(call("GET", "/api/messages/mine", null, messageTokenA)).get("total").asInt());
        assertEquals(
                1,
                data(call("GET", "/api/messages/mine?folder=archived", null, messageTokenA))
                        .get("total")
                        .asInt());
        data(
                call(
                        "PUT",
                        "/api/messages/archive",
                        json.writeValueAsString(Map.of("ids", List.of(id), "archived", false)),
                        messageTokenA));
        assertEquals(
                0,
                data(call(
                                "GET",
                                "/api/messages/tasks?keyword=no-such-message-title",
                                null,
                                messageAdmin))
                        .get("total")
                        .asInt());
        var receipts =
                data(
                        call(
                                "GET",
                                "/api/messages/tasks/"
                                        + messageTask
                                        + "/receipts?receiptStatus=CONFIRMED",
                                null,
                                messageAdmin));
        assertEquals(1, receipts.get("total").asInt());
        assertEquals(messageUserA, receipts.get("records").get(0).get("userId").asLong());
        assertEquals(
                403,
                call("GET", "/api/messages/tasks/" + messageTask + "/receipts", null, messageTokenA)
                        .statusCode());
        assertEquals(
                400,
                call("GET", "/api/messages/mine?folder=wrong", null, messageTokenA).statusCode());
    }

    @Test
    @Order(22)
    void messagePermissionsAndRecipientEnumerationStayScoped() throws Exception {
        long senderRole =
                data(call(
                                "POST",
                                "/api/system/roles",
                                "{\"name\":\"本人消息发布\",\"code\":\"MESSAGE_SENDER\",\"status\":1,\"dataScope\":5}",
                                messageAdmin))
                        .asLong();
        var permissions =
                db.queryForList(
                        "SELECT id FROM sys_menu WHERE permission IN"
                            + " ('message:send','message:query') AND type='BUTTON' AND deleted=0",
                        Long.class);
        data(
                call(
                        "PUT",
                        "/api/system/roles/" + senderRole + "/menus",
                        json.writeValueAsString(Map.of("ids", permissions)),
                        messageAdmin));
        data(
                call(
                        "PUT",
                        "/api/system/users/" + messageUserA + "/roles",
                        json.writeValueAsString(Map.of("ids", List.of(senderRole))),
                        messageAdmin));
        messageTokenA = login("message_a", "123456");
        assertEquals(
                1,
                data(call("GET", "/api/messages/audience/users", null, messageTokenA))
                        .get("total")
                        .asInt());
        Map<String, Object> draft = messageDraft(List.of());
        draft.put(
                "audience",
                Map.of(
                        "userIds",
                        List.of(),
                        "deptIds",
                        List.of(),
                        "roleIds",
                        List.of(),
                        "postIds",
                        List.of(),
                        "excludedIds",
                        List.of(),
                        "includeChildren",
                        true,
                        "all",
                        true));
        long id =
                data(call(
                                "POST",
                                "/api/messages/tasks",
                                json.writeValueAsString(draft),
                                messageTokenA))
                        .get("id")
                        .asLong();
        assertEquals(
                403,
                call("POST", "/api/messages/tasks/" + id + "/preview", null, messageTokenA)
                        .statusCode());
        assertEquals(
                403,
                call(
                                "POST",
                                "/api/messages/audience/selected",
                                json.writeValueAsString(List.of(messageUserB)),
                                messageTokenA)
                        .statusCode());
        assertEquals(
                403,
                call("GET", "/api/messages/tasks/" + messageTask, null, messageTokenA)
                        .statusCode());
        assertEquals(
                403,
                call(
                                "POST",
                                "/api/messages/tasks/" + messageTask + "/withdraw",
                                null,
                                messageTokenA)
                        .statusCode());
    }

    @Test
    @Order(23)
    void scheduledCancelAndDisabledRecipientsAreRealLifecycleStates() throws Exception {
        var draft = messageDraft(List.of(messageUserB));
        draft.put(
                "scheduledAt",
                java.time.LocalDateTime.now()
                        .plusDays(1)
                        .format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm:ss")));
        long scheduled = publishMessage(draft);
        delivery.deliverDue();
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM message_record WHERE task_id=?",
                        Integer.class,
                        scheduled));
        data(call("POST", "/api/messages/tasks/" + scheduled + "/cancel", null, messageAdmin));
        assertEquals(
                "CANCELLED",
                data(call("GET", "/api/messages/tasks/" + scheduled, null, messageAdmin))
                        .get("status")
                        .asString());
        data(call("DELETE", "/api/messages/tasks/" + scheduled, null, messageAdmin));
        assertEquals(
                scheduled,
                db.queryForObject(
                        "SELECT deleted FROM message_task WHERE id=?", Long.class, scheduled));
        long queued = publishMessage(messageDraft(List.of(messageUserC)));
        db.update("UPDATE sys_user SET status=0 WHERE id=?", messageUserC);
        delivery.deliverDue();
        assertEquals(
                "SKIPPED",
                db.queryForObject(
                        "SELECT status FROM message_recipient WHERE task_id=?",
                        String.class,
                        queued));
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM message_record WHERE task_id=?",
                        Integer.class,
                        queued));
        db.update("UPDATE sys_user SET status=1 WHERE id=?", messageUserC);
        var expired = messageDraft(List.of(messageUserB));
        expired.put(
                "expiresAt",
                java.time.LocalDateTime.now()
                        .plusMinutes(1)
                        .format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm:ss")));
        long expiredId = publishMessage(expired);
        db.update(
                "UPDATE message_task SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE"
                        + " id=?",
                expiredId);
        delivery.deliverDue();
        assertEquals(
                "SKIPPED",
                db.queryForObject(
                        "SELECT status FROM message_recipient WHERE task_id=?",
                        String.class,
                        expiredId));
    }

    @Test
    @Order(24)
    void withdrawalPreservesReceiptsButBlocksBodyAndAttachments() throws Exception {
        String boundary = "MessageAttachmentBoundary";
        String multipart =
                "--"
                        + boundary
                        + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\";"
                        + " filename=\"message-proof.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\n"
                        + "message attachment proof\r\n"
                        + "--"
                        + boundary
                        + "--\r\n";
        var upload =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/file/objects"))
                        .header("Authorization", "Bearer " + messageAdmin)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(multipart))
                        .build();
        messageAttachment =
                data(client.send(upload, HttpResponse.BodyHandlers.ofString())).get("id").asLong();
        var draft = messageDraft(List.of(messageUserB));
        draft.put("attachmentIds", List.of(messageAttachment));
        long id = publishMessage(draft);
        delivery.deliverDue();
        assertEquals(
                "message attachment proof",
                call(
                                "GET",
                                "/api/messages/tasks/" + id + "/attachments/" + messageAttachment,
                                null,
                                messageTokenB)
                        .body());
        assertEquals(
                403,
                call(
                                "GET",
                                "/api/messages/tasks/" + id + "/attachments/" + messageAttachment,
                                null,
                                messageTokenA)
                        .statusCode());
        assertEquals(
                400,
                call("DELETE", "/api/file/objects/" + messageAttachment, null, messageAdmin)
                        .statusCode());
        long receipt =
                db.queryForObject("SELECT id FROM message_record WHERE task_id=?", Long.class, id);
        data(call("GET", "/api/messages/mine/" + receipt, null, messageTokenB));
        data(call("PUT", "/api/messages/mine/" + receipt + "/confirm", null, messageTokenB));
        data(call("POST", "/api/messages/tasks/" + id + "/withdraw", null, messageAdmin));
        var withdrawn = data(call("GET", "/api/messages/mine/" + receipt, null, messageTokenB));
        assertTrue(withdrawn.get("content").isNull());
        assertFalse(withdrawn.get("confirmedAt").isNull());
        assertEquals(0, withdrawn.get("attachments").size());
        assertEquals(
                403,
                call(
                                "GET",
                                "/api/messages/tasks/" + id + "/attachments/" + messageAttachment,
                                null,
                                messageTokenB)
                        .statusCode());
        assertEquals(
                400,
                call("PUT", "/api/messages/mine/" + receipt + "/confirm", null, messageTokenB)
                        .statusCode());
        data(call("DELETE", "/api/file/objects/" + messageAttachment, null, messageAdmin));
    }

    @Test
    @Order(25)
    void deliveryRollbackBoundedRetryAndResumeDoNotDuplicate() throws Exception {
        long id = publishMessage(messageDraft(List.of(messageUserB)));
        // 真实唯一键冲突迫使整批回滚，不通过替身抛错伪造重试成功。
        db.update(
                "INSERT INTO message_record(task_id,recipient_id,sender_id,title,content,read_flag)"
                        + " VALUES(?,?,1,'冲突测试','',FALSE)",
                id,
                messageUserB);
        for (int attempt = 0; attempt < 3; attempt++) {
            db.update(
                    "UPDATE message_task SET next_attempt_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP)"
                            + " WHERE id=?",
                    id);
            delivery.deliverDue();
        }
        assertEquals(
                "FAILED",
                db.queryForObject("SELECT status FROM message_task WHERE id=?", String.class, id));
        assertEquals(
                "FAILED",
                db.queryForObject(
                        "SELECT status FROM message_recipient WHERE task_id=?", String.class, id));
        db.update("DELETE FROM message_record WHERE task_id=?", id);
        data(call("POST", "/api/messages/tasks/" + id + "/retry", null, messageAdmin));
        delivery.deliverDue();
        delivery.deliverDue();
        assertEquals(
                "SENT",
                db.queryForObject("SELECT status FROM message_task WHERE id=?", String.class, id));
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM message_record WHERE task_id=?", Integer.class, id));
    }

    @Test
    @Order(26)
    void templatePreviewEscapesVariablesAndDeleteKeepsPublishedContent() throws Exception {
        long id =
                data(call(
                                "POST",
                                "/api/messages/templates",
                                "{\"code\":\"message_html_template\",\"name\":\"富文本模板\",\"title\":\"通知"
                                    + " ${name}\",\"content\":\"<p>你好"
                                    + " ${name}</p>\",\"enabled\":true}",
                                messageAdmin))
                        .asLong();
        var rendered =
                data(
                        call(
                                "POST",
                                "/api/messages/templates/" + id + "/preview",
                                json.writeValueAsString(
                                        Map.of(
                                                "variables",
                                                Map.of("name", "<img src=x onerror=alert(1)>"))),
                                messageAdmin));
        assertFalse(rendered.get("content").asString().contains("<img"));
        assertEquals(
                400,
                call(
                                "POST",
                                "/api/messages/templates/" + id + "/preview",
                                "{\"variables\":{}}",
                                messageAdmin)
                        .statusCode());
        data(call("DELETE", "/api/messages/templates/" + id, null, messageAdmin));
        assertEquals(
                id,
                db.queryForObject(
                        "SELECT deleted FROM message_template WHERE id=?", Long.class, id));
        assertEquals(
                0,
                data(call(
                                "GET",
                                "/api/messages/templates/page?keyword=message_html_template",
                                null,
                                messageAdmin))
                        .get("total")
                        .asInt());
        assertFalse(
                data(call("GET", "/api/messages/tasks/" + messageTask, null, messageAdmin))
                        .get("content")
                        .asString()
                        .isBlank());
    }

    @Test
    @Order(27)
    void scheduledDeliveryAndConcurrentWorkersKeepExactlyOneReceipt() throws Exception {
        var draft = messageDraft(List.of(messageUserA, messageUserB));
        draft.put(
                "scheduledAt",
                java.time.LocalDateTime.now()
                        .plusDays(1)
                        .format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm:ss")));
        long id = publishMessage(draft);
        delivery.deliverDue();
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM message_record WHERE task_id=?", Integer.class, id));
        // 推进真实数据库任务时间，再用两个后台执行线程竞争同一任务行锁。
        db.update(
                "UPDATE message_task SET"
                    + " scheduled_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP),next_attempt_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP)"
                    + " WHERE id=?",
                id);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> delivery.deliverDue());
            var second = workers.submit(() -> delivery.deliverDue());
            first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            second.get(10, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(
                "SENT",
                db.queryForObject("SELECT status FROM message_task WHERE id=?", String.class, id));
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM message_record WHERE task_id=?", Integer.class, id));
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM message_recipient WHERE task_id=? AND"
                                + " status='DELIVERED' AND notified=TRUE",
                        Integer.class,
                        id));
    }

    @Test
    @Order(28)
    void repeatedDetailReadsPublishOnlyOneUnreadChange(
            @Autowired org.springframework.context.ConfigurableApplicationContext context)
            throws Exception {
        // 使用真实发布和投递产生未读消息，排除投递通知对本次阅读断言的影响。
        long task = publishMessage(messageDraft(List.of(messageUserB)));
        delivery.deliverDue();
        long receipt =
                db.queryForObject(
                        "SELECT id FROM message_record WHERE task_id=?", Long.class, task);
        var changes = new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.context.ApplicationListener<
                        org.springframework.context.ApplicationEvent>
                observer =
                        event -> {
                            if (event
                                            instanceof
                                            org.springframework.context.PayloadApplicationEvent<?>
                                                    payload
                                    && payload.getPayload()
                                            instanceof sail.message.service.MessageService.Sent sent
                                    && sent.recipients().contains(messageUserB))
                                changes.incrementAndGet();
                        };
        context.addApplicationListener(observer);
        try {
            // 重复详情及批量已读不产生第二次状态推送，防止实时刷新形成请求循环。
            var first = data(call("GET", "/api/messages/mine/" + receipt, null, messageTokenB));
            var second = data(call("GET", "/api/messages/mine/" + receipt, null, messageTokenB));
            data(
                    call(
                            "PUT",
                            "/api/messages/read",
                            json.writeValueAsString(Map.of("ids", List.of(receipt))),
                            messageTokenB));
            assertEquals(1, changes.get());
            assertEquals(first.get("readAt"), second.get("readAt"));
        } finally {
            context.removeApplicationListener(observer);
        }
    }

    /** 公告补读与持久化展示回执通过真实 HTTP、数据库和投递服务验证。 */
    @Test
    @Order(29)
    void popupAnnouncementPersistsPresentationAndRespectsOwnershipAndWithdrawal() throws Exception {
        var draft = messageDraft(List.of(messageUserB));
        draft.put("displayMode", "POPUP");
        draft.put("requireConfirmation", true);
        long task = publishMessage(draft);
        delivery.deliverDue();
        long receipt =
                db.queryForObject(
                        "SELECT id FROM message_record WHERE task_id=?", Long.class, task);
        var pending = data(call("GET", "/api/messages/announcements", null, messageTokenB));
        assertTrue(pending.toString().contains("\"id\":" + receipt));
        assertEquals(
                404,
                call("PUT", "/api/messages/mine/" + receipt + "/presented", null, messageTokenA)
                        .statusCode());
        // 普通阅读不能提前吞掉公告，只有成功展示后才写独立回执。
        data(call("GET", "/api/messages/mine/" + receipt, null, messageTokenB));
        assertTrue(
                data(call("GET", "/api/messages/announcements", null, messageTokenB))
                        .toString()
                        .contains("\"id\":" + receipt));
        data(call("PUT", "/api/messages/mine/" + receipt + "/presented", null, messageTokenB));
        var first =
                db.queryForObject(
                        "SELECT popup_seen_at FROM message_record WHERE id=?",
                        java.sql.Timestamp.class,
                        receipt);
        data(call("PUT", "/api/messages/mine/" + receipt + "/presented", null, messageTokenB));
        assertEquals(
                first,
                db.queryForObject(
                        "SELECT popup_seen_at FROM message_record WHERE id=?",
                        java.sql.Timestamp.class,
                        receipt));
        assertNull(
                db.queryForObject(
                        "SELECT confirmed_at FROM message_record WHERE id=?",
                        java.sql.Timestamp.class,
                        receipt));
        assertFalse(
                data(call("GET", "/api/messages/announcements", null, messageTokenB))
                        .toString()
                        .contains("\"id\":" + receipt));
        // 撤回后待展示公告也消失，不产生虚假的展示事实。
        long second =
                publishMessage(
                        draft = new java.util.LinkedHashMap<>(messageDraft(List.of(messageUserB))));
        db.update("UPDATE message_task SET display_mode='POPUP' WHERE id=?", second);
        delivery.deliverDue();
        data(call("POST", "/api/messages/tasks/" + second + "/withdraw", null, messageAdmin));
        long other =
                db.queryForObject(
                        "SELECT id FROM message_record WHERE task_id=?", Long.class, second);
        assertEquals(
                400,
                call("PUT", "/api/messages/mine/" + other + "/presented", null, messageTokenB)
                        .statusCode());
        assertFalse(
                data(call("GET", "/api/messages/announcements", null, messageTokenB))
                        .toString()
                        .contains("\"id\":" + other));
    }

    /** 真实二进制 Office 解析覆盖内容读取，不使用预设响应替代文档预览。 */
    @Test
    @Order(30)
    void officeTextPreviewReadsActualSlidesAndWorkbook(
            @Autowired sail.file.service.OfficeTextPreview preview) throws Exception {
        try (var slides = new org.apache.poi.xslf.usermodel.XMLSlideShow();
                var output = new java.io.ByteArrayOutputStream()) {
            slides.createSlide().createTextBox().setText("真实更新公告内容");
            slides.write(output);
            assertTrue(
                    preview.read("公告.pptx", new java.io.ByteArrayInputStream(output.toByteArray()))
                            .contains("真实更新公告内容"));
        }
        try (var book = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
                var output = new java.io.ByteArrayOutputStream()) {
            book.createSheet("真实工作表").createRow(0).createCell(0).setCellValue("预览单元格");
            book.write(output);
            assertTrue(
                    preview.read("资料.xls", new java.io.ByteArrayInputStream(output.toByteArray()))
                            .contains("预览单元格"));
        }
    }

    /** 站点标志上传和文件引用保护使用真实图片字节与鉴权 HTTP 验证。 */
    @Test
    @Order(31)
    void uploadedWebsiteLogoIsPublicAndCannotBeDeletedWhileConfigured() throws Exception {
        String session = login("admin", "SailTestOnly!2026");
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(
                new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "png",
                output);
        String boundary = "SailLogoBoundary";
        var body = new java.io.ByteArrayOutputStream();
        body.write(
                ("--"
                                + boundary
                                + "\r\n"
                                + "Content-Disposition: form-data; name=\"file\";"
                                + " filename=\"brand.png\"\r\n"
                                + "Content-Type: image/png\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
        body.write(output.toByteArray());
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var response =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/api/file/website-logo"))
                                .header("Authorization", "Bearer " + session)
                                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        var uploaded = data(response);
        long id = uploaded.get("id").asLong();
        String url = uploaded.get("url").asString();
        var prior =
                data(call("GET", "/api/system/configs/website", null, session))
                        .get("website.logo")
                        .asString();
        try {
            data(
                    call(
                            "PUT",
                            "/api/system/configs/settings",
                            json.writeValueAsString(Map.of("website.logo", url)),
                            session));
            assertEquals(
                    url,
                    data(call("GET", "/api/system/configs/website", null, session))
                            .get("website.logo")
                            .asString());
            var publicImage = call("GET", url, null, null);
            assertEquals(200, publicImage.statusCode());
            assertEquals(
                    "image/png", publicImage.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(
                    publicImage
                            .headers()
                            .firstValue("Content-Disposition")
                            .orElseThrow()
                            .startsWith("inline"));
            assertEquals(
                    400, call("DELETE", "/api/file/objects/" + id, null, session).statusCode());
        } finally {
            data(
                    call(
                            "PUT",
                            "/api/system/configs/settings",
                            json.writeValueAsString(Map.of("website.logo", prior)),
                            session));
            data(call("DELETE", "/api/file/objects/" + id, null, session));
        }
    }

    @AfterAll
    static void stop() throws Exception {
        /* 释放测试专用监听端口。 */
        REDIS.stop();
    }
}
