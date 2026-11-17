package io.github.ayongbox.daos.web.support;

import io.github.ayongbox.daos.runtime.auth.AuthenticationException;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 接口异常到 HTTP 状态的统一映射。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleUnauthorized(AuthenticationException error) {
        return new ApiError("UNAUTHORIZED", error.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleForbidden(AccessDeniedException error) {
        return new ApiError("FORBIDDEN", error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequest(IllegalArgumentException error) {
        return new ApiError("BAD_REQUEST", error.getMessage());
    }
}
