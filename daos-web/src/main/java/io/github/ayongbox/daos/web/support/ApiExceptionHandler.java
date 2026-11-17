package io.github.ayongbox.daos.web.support;

import io.github.ayongbox.baseon.core.model.CommonErrorCode;
import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.daos.runtime.auth.AuthenticationException;
import io.github.ayongbox.daos.runtime.exec.WriteConflictException;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;
import io.github.ayongbox.daos.runtime.permission.PermissionRewriteException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Daos 领域异常到 HTTP 状态与 {@link R} 错误码的映射。
 *
 * <p>只处理本项目的领域异常，并优先于 Baseon 的全局异常处理器；
 * 其余异常仍交给 Baseon 统一处理，避免出现两套响应格式。</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<R<Void>> handleUnauthorized(AuthenticationException error) {
        return fail(401, CommonErrorCode.UNAUTHORIZED, error.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<R<Void>> handleForbidden(AccessDeniedException error) {
        return fail(403, CommonErrorCode.FORBIDDEN, error.getMessage());
    }

    @ExceptionHandler(PermissionRewriteException.class)
    public ResponseEntity<R<Void>> handlePermissionDenied(PermissionRewriteException error) {
        return fail(403, CommonErrorCode.FORBIDDEN, error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<R<Void>> handleBadRequest(IllegalArgumentException error) {
        return fail(400, CommonErrorCode.PARAM_INVALID, error.getMessage());
    }

    @ExceptionHandler(WriteConflictException.class)
    public ResponseEntity<R<Void>> handleWriteConflict(WriteConflictException error) {
        return fail(409, CommonErrorCode.DUPLICATE_REQUEST, error.getMessage());
    }

    private static ResponseEntity<R<Void>> fail(int status, CommonErrorCode code, String message) {
        String text = (message == null || message.isBlank()) ? code.getMessage() : message;
        return ResponseEntity.status(status).body(R.fail(code.getCode(), text));
    }
}
