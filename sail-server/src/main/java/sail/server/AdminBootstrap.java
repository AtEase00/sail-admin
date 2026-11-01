package sail.server;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import sail.system.service.SystemBootstrapService;

/** 根据当前环境配置首次创建内置管理员，不覆盖已有凭据。 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
    private final SystemBootstrapService bootstrap;

    @Value("${sail.bootstrap.username}")
    private String username;

    @Value("${sail.bootstrap.password}")
    private String password;

    @Value("${sail.bootstrap.salt}")
    private String salt;

    @Override
    public void run(ApplicationArguments args) {
        // 启动层只传递明确配置，不持有业务 Mapper 或编造管理员密码。
        bootstrap.initialize(username, password, salt);
    }
}
