package sail.framework.log;

import jakarta.servlet.http.*;

import lombok.RequiredArgsConstructor;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

import sail.common.core.*;

/** 参数绑定前标记操作元数据，确保校验异常和授权失败同样进入操作审计。 */
@RequiredArgsConstructor
public class OperationLogInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final ActorProvider actors;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 先识别操作，再由安全拦截器校验，失败时仍保留操作名称及类型。
        registry.addInterceptor(this).addPathPatterns("/api/**").order(-100);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        var context = RequestAuditContext.current();
        if (context == null || !(handler instanceof HandlerMethod method)) return true;
        var operation =
                AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), OperationLog.class);
        if (operation == null) return true;
        context.setUserId(actors.currentUserId());
        context.setAction(operation.value());
        // 特殊业务显式声明，常规增删改查按实际 HTTP 语义确定。
        context.setOperationType(
                operation.type() == OperationType.AUTO
                        ? switch (request.getMethod()) {
                            case "POST" -> OperationType.INSERT;
                            case "PUT", "PATCH" -> OperationType.UPDATE;
                            case "DELETE" -> OperationType.DELETE;
                            case "GET" -> OperationType.QUERY;
                            default -> OperationType.OTHER;
                        }
                        : operation.type());
        return true;
    }
}
