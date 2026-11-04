package sail.job;

import static org.junit.jupiter.api.Assertions.*;

import com.github.fppt.jedismock.RedisServer;

import org.junit.jupiter.api.*;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;

import sail.common.core.BusinessException;
import sail.job.api.*;
import sail.server.SailApplication;

import tools.jackson.databind.*;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

@SpringBootTest(
        classes = SailApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(JobSchedulerIntegrationTest.Handlers.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class JobSchedulerIntegrationTest {
    static final RedisServer REDIS;

    static {
        try {
            REDIS = RedisServer.newRedisServer().start();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry r) {
        r.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:jobs;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE");
        r.add("spring.sql.init.mode", () -> "always");
        r.add(
                "spring.sql.init.schema-locations",
                () -> "classpath:db/base.sql,classpath:db/quartz-h2-types.sql");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", REDIS::getBindPort);
        r.add("spring.quartz.scheduler-name", () -> "sail-job-test");
        r.add("spring.quartz.auto-startup", () -> true);
        r.add("sail.job.enabled", () -> true);
        r.add("logging.level.sail.job", () -> "DEBUG");
        r.add("sail.job.maintenance-delay-ms", () -> 1000);
        r.add("sail.message.delivery-enabled", () -> false);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Handlers {
        @Bean
        ControlledHandler controlledHandler(
                ObjectMapper json, org.springframework.jdbc.core.JdbcTemplate db) {
            return new ControlledHandler(json, db);
        }

        @Bean
        @Primary
        sail.server.config.LocalRedisTokenDao tokenDao() {
            return new sail.server.config.LocalRedisTokenDao("sail-job-test");
        }
    }

    static class ControlledHandler implements JobHandler {
        final ObjectMapper json;
        final org.springframework.jdbc.core.JdbcTemplate db;

        ControlledHandler(ObjectMapper json, org.springframework.jdbc.core.JdbcTemplate db) {
            this.json = json;
            this.db = db;
        }

        public String code() {
            return "test.controlled";
        }

        public String name() {
            return "隔离测试处理器";
        }

        public String description() {
            return "仅测试资源可见";
        }

        public boolean retryable() {
            return true;
        }

        public String validate(String parameters) {
            json.readTree(parameters);
            return parameters;
        }

        @Transactional
        public JobResult execute(JobContext context, String parameters) throws Exception {
            var p = json.readTree(parameters);
            long wait = p.has("delay") ? p.get("delay").asLong() : 0;
            long end = System.currentTimeMillis() + wait;
            while (System.currentTimeMillis() < end) {
                context.checkCancelled();
                Thread.sleep(50);
            }
            if (p.has("fail")) {
                db.update(
                        "UPDATE sys_config SET config_value='should-rollback' WHERE"
                            + " config_key='website.title'");
                throw new IllegalStateException("sensitive-payload-must-not-be-exposed");
            }
            return new JobResult(2, 0, "测试成功");
        }
    }

    @Autowired JobService service;
    @Autowired JobStore store;
    @Autowired JobSynchronizer sync;
    @Autowired Scheduler scheduler;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newHttpClient();

    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 20000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("等待状态超时");
            Thread.sleep(100);
        }
    }

    JobModels.Input input(String parameters, int timeout, int retries, long version) {
        return new JobModels.Input(
                "测试任务",
                "测试",
                "test.controlled",
                parameters,
                "INTERVAL",
                "",
                3600,
                "Asia/Shanghai",
                "SKIP",
                timeout,
                retries,
                10,
                30,
                version);
    }

    long create(String params, int timeout, int retries) {
        long id = service.save(null, input(params, timeout, retries, 0));
        service.control(id, new JobModels.Control(1L, "ENABLED"));
        sync.sync(id);
        return id;
    }

    long run(long id) {
        return service.trigger(id, store.job(id, false).version(), null, 1, "测试操作者");
    }

    void pause(long id) {
        service.control(id, new JobModels.Control(store.job(id, false).version(), "PAUSED"));
        sync.sync(id);
    }

    HttpResponse<String> http(String method, String path, String token, Object body)
            throws Exception {
        var b =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
                        .header("Content-Type", "application/json");
        if (token != null) b.header("Authorization", "Bearer " + token);
        return client.send(
                b.method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(
                                                json.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    String login(String user, String password) throws Exception {
        return json.readTree(
                        http(
                                        "POST",
                                        "/system/auth/login",
                                        null,
                                        Map.of("username", user, "password", password))
                                .body())
                .get("data")
                .get("tokenValue")
                .asString();
    }

    @Test
    void permissionValidationAndCronPreview() throws Exception {
        assertEquals(401, http("GET", "/system/jobs", null, null).statusCode());
        String admin = login("admin", "SailTestOnly!2026");
        assertEquals(
                200,
                http(
                                "POST",
                                "/system/users",
                                admin,
                                Map.of(
                                        "username",
                                        "job-worker",
                                        "password",
                                        "JobWorker!123",
                                        "nickname",
                                        "任务普通账号",
                                        "status",
                                        1))
                        .statusCode());
        String worker = login("job-worker", "JobWorker!123");
        for (String path :
                List.of("/system/jobs", "/system/jobs/handlers", "/system/jobs/executions"))
            assertEquals(403, http("GET", path, worker, null).statusCode());
        assertEquals(403, http("POST", "/system/jobs", worker, input("{}", 60, 0, 0)).statusCode());
        var valid = input("{}", 60, 0, 0);
        assertEquals(200, http("POST", "/system/jobs/preview", admin, valid).statusCode());
        var invalid = new HashMap<String, Object>(json.convertValue(valid, Map.class));
        invalid.put("zoneId", "invalid-zone");
        assertEquals(400, http("POST", "/system/jobs/preview", admin, invalid).statusCode());
        invalid.put("zoneId", "Asia/Shanghai");
        invalid.put("handler", "java.lang.Runtime");
        assertEquals(400, http("POST", "/system/jobs", admin, invalid).statusCode());
        invalid.put("handler", "system.diagnostic");
        invalid.put("parameters", "{\"secret\":\"do-not-accept\"}");
        assertEquals(400, http("POST", "/system/jobs", admin, invalid).statusCode());
        invalid.put("parameters", "{}");
        invalid.put("scheduleType", "CRON");
        invalid.put("cron", "* * * * * ?");
        assertEquals(400, http("POST", "/system/jobs/preview", admin, invalid).statusCode());
        invalid.put("cron", "0 0/5 * * * ?");
        assertEquals(
                5,
                json.readTree(http("POST", "/system/jobs/preview", admin, invalid).body())
                        .get("data")
                        .size());
    }

    @Test
    void jdbcPersistenceAndDuplicateManualTriggerAreSerialized() throws Exception {
        long id = create("{\"delay\":1500}", 60, 0);
        assertTrue(scheduler.checkExists(JobSchedules.key(id)));
        assertEquals(
                1,
                store.db()
                        .queryForObject(
                                "SELECT COUNT(*) FROM QRTZ_JOB_DETAILS WHERE JOB_NAME=?",
                                Integer.class,
                                "job-" + id));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a =
                    pool.submit(
                            () -> {
                                try {
                                    return run(id);
                                } catch (BusinessException e) {
                                    return -1L;
                                }
                            });
            var b =
                    pool.submit(
                            () -> {
                                try {
                                    return run(id);
                                } catch (BusinessException e) {
                                    return -1L;
                                }
                            });
            long first = a.get(), second = b.get();
            assertTrue((first > 0) ^ (second > 0));
            long execution = Math.max(first, second);
            sync.dispatch(execution);
            await(() -> store.run(execution).finishedAt() != null);
            assertEquals("SUCCESS", store.run(execution).status());
            assertEquals(2, store.run(execution).succeeded());
            assertNull(store.job(id, false).activeRunId());
        } finally {
            pause(id);
        }
    }

    @Test
    void pauseCancelsQueuedAndOptimisticVersionRejectsStaleEdits() throws Exception {
        scheduler.standby();
        long id = create("{}", 60, 0);
        try {
            long execution = run(id);
            sync.dispatch(execution);
            pause(id);
            assertEquals("SKIPPED", store.run(execution).status());
            assertNull(store.job(id, false).activeRunId());
            assertThrows(BusinessException.class, () -> service.save(id, input("{}", 60, 0, 1)));
        } finally {
            scheduler.start();
        }
    }

    @Test
    void editingRunningTaskKeepsSnapshotAndPreventsOldVersionRetry() throws Exception {
        long id = create("{\"delay\":1500,\"fail\":true}", 60, 1);
        long execution = run(id);
        sync.dispatch(execution);
        await(() -> "RUNNING".equals(store.run(execution).status()));
        service.save(id, input("{}", 60, 1, store.job(id, false).version()));
        sync.sync(id);
        assertThrows(BusinessException.class, () -> run(id));
        await(() -> store.run(execution).finishedAt() != null);
        assertEquals("FAILED", store.run(execution).status());
        assertEquals(2, store.run(execution).jobVersion());
        assertNull(store.job(id, false).activeRunId());
        assertFalse(store.run(execution).summary().contains("sensitive-payload"));
        assertNotEquals(
                "should-rollback",
                store.db()
                        .queryForObject(
                                "SELECT config_value FROM sys_config WHERE"
                                    + " config_key='website.title'",
                                String.class));
        pause(id);
    }

    @Test
    void failureCreatesBoundedRetryAndPreservesOriginalRecord() throws Exception {
        long id = create("{\"fail\":true}", 60, 1);
        long execution = run(id);
        sync.dispatch(execution);
        await(() -> store.run(execution).finishedAt() != null);
        Long retry = store.job(id, false).activeRunId();
        assertNotNull(retry);
        assertNotEquals(execution, retry);
        assertEquals(execution, store.run(retry).parentRunId());
        assertEquals("QUEUED", store.run(retry).status());
        assertTrue(store.run(retry).scheduledAt() - store.run(execution).finishedAt() >= 10000);
        await(() -> store.run(retry).finishedAt() != null);
        assertEquals("FAILED", store.run(retry).status());
        assertNull(store.job(id, false).activeRunId());
        assertEquals(
                2,
                store.db()
                        .queryForObject(
                                "SELECT COUNT(*) FROM sys_job_execution WHERE job_id=?",
                                Integer.class,
                                id));
        pause(id);
    }

    @Test
    void timeoutRequestsCancellationAndNeverAutoRetries() throws Exception {
        long id = create("{\"delay\":8000}", 5, 3);
        long execution = run(id);
        sync.dispatch(execution);
        await(() -> store.run(execution).finishedAt() != null);
        assertEquals("TIMED_OUT", store.run(execution).status());
        assertNull(store.job(id, false).activeRunId());
        assertEquals(
                1,
                store.db()
                        .queryForObject(
                                "SELECT COUNT(*) FROM sys_job_execution WHERE job_id=?",
                                Integer.class,
                                id));
        pause(id);
    }

    @Test
    void staleExecutionRemainsBlockedUntilExplicitResolution() throws Exception {
        scheduler.standby();
        long id = create("{}", 60, 0);
        try {
            long execution = run(id);
            store.db()
                    .update(
                            "UPDATE sys_job_execution SET"
                                + " status='RUNNING',started_at=?,heartbeat_at=? WHERE id=?",
                            System.currentTimeMillis() - 200000,
                            System.currentTimeMillis() - 200000,
                            execution);
            sync.maintain(90000);
            assertEquals("UNKNOWN", store.run(execution).status());
            assertThrows(BusinessException.class, () -> run(id));
            service.resolve(execution, "隔离测试中的原进程已确认不存在");
            assertNull(store.job(id, false).activeRunId());
            assertEquals("UNKNOWN", store.run(execution).status());
            assertNotNull(store.run(execution).finishedAt());
            pause(id);
        } finally {
            scheduler.start();
        }
    }

    @Test
    void retentionPurgesCompletedRecordsButPreservesActiveExecution() throws Exception {
        long id = create("{}", 60, 0);
        long execution = run(id);
        sync.dispatch(execution);
        await(() -> store.run(execution).finishedAt() != null);
        store.db()
                .update(
                        "UPDATE sys_job_execution SET finished_at=? WHERE id=?",
                        System.currentTimeMillis() - 40L * 86400000,
                        execution);
        sync.maintain(90000);
        assertThrows(BusinessException.class, () -> store.run(execution));
        pause(id);
    }
}
