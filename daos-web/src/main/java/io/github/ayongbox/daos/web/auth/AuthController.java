package io.github.ayongbox.daos.web.auth;

import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.runtime.auth.AuthenticationException;
import io.github.ayongbox.daos.runtime.auth.AuthenticationService;
import io.github.ayongbox.daos.web.support.BearerTokens;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录、登出与当前会话查询。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest body, HttpServletRequest request) {
        requireText(body.tenantId(), "tenantId");
        requireText(body.username(), "username");
        requireText(body.password(), "password");

        AuthenticationService.LoginResult result = authenticationService.login(
                body.tenantId(), body.username(), body.password(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        Session session = result.session();
        return new LoginResponse(
                result.token(), session.tenantId(), session.subjectId(), session.expiresAt());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String token = BearerTokens.resolve(request)
                .orElseThrow(() -> new AuthenticationException("缺少访问令牌"));
        authenticationService.logout(token);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    public SessionView me(HttpServletRequest request) {
        Session session = (Session) request.getAttribute(SessionAuthInterceptor.SESSION_ATTRIBUTE);
        if (session == null) {
            throw new AuthenticationException("未登录或会话已过期");
        }
        return SessionView.of(session);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
