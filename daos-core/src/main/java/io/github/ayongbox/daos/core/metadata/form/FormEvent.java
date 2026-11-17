package io.github.ayongbox.daos.core.metadata.form;

import java.util.Map;
import java.util.Objects;

/**
 * 表单事件钩子。
 */
public record FormEvent(
        String id,
        FormEventType event,
        HandlerType handlerType,
        String expression,
        String script,
        String url,
        String method,
        Map<String, String> headers,
        boolean async,
        int timeoutMs,
        int retry,
        ErrorStrategy onError,
        String condition,
        boolean enabled) {

    public FormEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(handlerType, "handlerType");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        method = method == null ? "POST" : method;
        timeoutMs = timeoutMs == 0 ? 3000 : timeoutMs;
        onError = onError == null ? ErrorStrategy.ABORT : onError;
    }
}
