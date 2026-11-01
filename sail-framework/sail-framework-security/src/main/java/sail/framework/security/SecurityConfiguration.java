package sail.framework.security;

import cn.dev33.satoken.exception.*;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.*;

import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.*;

import sail.common.core.*;

import java.util.List;

/** Sa-Token 接口登录校验和权限桥接。 */
@org.springframework.boot.autoconfigure.AutoConfiguration
@org.springframework.context.annotation.Import({SecretCipher.class, SecurityExceptionHandler.class})
public class SecurityConfiguration implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 仅登录与渠道通知允许匿名，其他业务接口统一要求登录。
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/system/auth/login", "/api/pay/callback/**", "/api/file/public/**");
    }

    @Bean
    public StpInterface stpInterface(PermissionProvider provider) {
        // 每次权限校验读取系统真实权限，授权修改不依赖过期缓存。
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object id, String type) {
                return provider.permissions(Long.parseLong(id.toString()));
            }

            @Override
            public List<String> getRoleList(Object id, String type) {
                return provider.roles(Long.parseLong(id.toString()));
            }
        };
    }

    @Bean
    public ActorProvider actorProvider() {
        // 无认证上下文的调度线程返回空，不伪造操作者。
        return () -> StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 每次 encode 均通过 SecureRandom 生成独立盐，不使用共享固定盐或 AES 主密钥。
        return new BCryptPasswordEncoder(
                PasswordConstants.BCRYPT_STRENGTH, new java.security.SecureRandom());
    }
}
