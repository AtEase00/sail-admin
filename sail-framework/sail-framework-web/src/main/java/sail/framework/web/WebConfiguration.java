package sail.framework.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;

/** Web 追踪、统一响应记录与服务端时间输出配置。 */
@AutoConfiguration
@Import({GlobalExceptionHandler.class, AuditResponseAdvice.class})
public class WebConfiguration {
    /** 复用应用 JSON 配置生成脱敏日志，不修改业务对象。 */
    @Bean
    public LogSanitizer logSanitizer(ObjectMapper mapper) {
        return new LogSanitizer(mapper);
    }

    /** 追踪过滤器先于 MVC 安全拦截器执行，错误响应也具有相同追踪号。 */
    @Bean
    public FilterRegistrationBean<RequestTraceFilter> traceFilter(
            @Value("${sail.web.request-log-enabled:true}") boolean enabled,
            ApplicationEventPublisher events,
            LogSanitizer sanitizer) {
        var registration =
                new FilterRegistrationBean<>(new RequestTraceFilter(enabled, events, sanitizer));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /** 全局统一业务时间格式，保持 Java 时间类型及数据库精度不变。 */
    @Bean
    public JsonMapperBuilderCustomizer businessDateTime() {
        return builder ->
                builder.addModule(
                        new SimpleModule("sail-business-time")
                                .addSerializer(
                                        LocalDateTime.class,
                                        new LocalDateTimeSerializer(
                                                RequestLogConstants.DATE_TIME)));
    }
}
