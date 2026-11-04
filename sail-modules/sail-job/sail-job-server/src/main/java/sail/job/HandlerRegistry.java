package sail.job;

import org.springframework.stereotype.Component;

import sail.common.core.BusinessException;
import sail.job.api.JobHandler;

import java.util.*;

@Component
public class HandlerRegistry {
    private final Map<String, JobHandler> handlers = new TreeMap<>();

    public HandlerRegistry(List<JobHandler> entries) {
        for (var handler : entries) {
            if (!handler.code().matches("[a-z][a-z0-9_.-]{1,63}")
                    || handlers.putIfAbsent(handler.code(), handler) != null)
                throw new IllegalStateException("重复或不合法的任务处理器编码");
        }
    }

    public JobHandler get(String code) {
        var handler = handlers.get(code);
        if (handler == null) throw new BusinessException("任务处理器未注册或当前实例版本不匹配");
        return handler;
    }

    public List<Map<String, Object>> options() {
        return handlers.values().stream()
                .map(
                        h ->
                                Map.<String, Object>of(
                                        "code",
                                        h.code(),
                                        "name",
                                        h.name(),
                                        "description",
                                        h.description(),
                                        "retryable",
                                        h.retryable()))
                .toList();
    }
}
