package sail.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Sail Admin 模块化单体启动入口。 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "sail")
public class SailApplication {
    public static void main(String[] args) {
        // 装配基础设施和四个独立业务模块。
        SpringApplication.run(SailApplication.class, args);
    }
}
