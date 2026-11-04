package sail.job;

import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;

import sail.server.SailApplication;

import tools.jackson.databind.ObjectMapper;

/** 仅在测试 classpath 中运行，不打入生产 JAR；供真实 MySQL 双 JVM 恢复演练。 */
public class ClusterVerificationNode {
    public static void main(String[] args) {
        new SpringApplication(SailApplication.class, VerificationConfiguration.class).run(args);
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("job-verification")
    public static class VerificationConfiguration {
        @Bean
        JobSchedulerIntegrationTest.ControlledHandler controlledHandler(
                ObjectMapper json, org.springframework.jdbc.core.JdbcTemplate db) {
            return new JobSchedulerIntegrationTest.ControlledHandler(json, db);
        }
    }
}
