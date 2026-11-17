package io.github.ayongbox.daos.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ayongbox.baseon.core.context.SecurityUtil;
import io.github.ayongbox.baseon.core.model.CommonErrorCode;
import io.github.ayongbox.baseon.core.model.LoginUser;
import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.runtime.auth.AuthenticationService;
import io.github.ayongbox.daos.web.support.BearerTokens;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * 会话拦截器。校验 Bearer 令牌并把会话放入请求属性，供控制器读取。
 *
 * <p>登录接口是唯一公开路径；校验失败直接返回 401，不进入控制器。</p>
 */
public final class SessionAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_ATTRIBUTE = SessionAuthInterceptor.class.getName() + ".SESSION";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login", "/api/files/download");

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public SessionAuthInterceptor(AuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (PUBLIC_PATHS.contains(servletPath(request))) {
            return true;
        }
        Optional<Session> session = BearerTokens.resolve(request)
                .flatMap(authenticationService::authenticate);
        if (session.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    R.fail(CommonErrorCode.UNAUTHORIZED.getCode(), "未登录或会话已过期"));
            return false;
        }
        request.setAttribute(SESSION_ATTRIBUTE, session.get());
        bindBaseonLoginUser(session.get());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        SecurityUtil.clear();
    }

    /**
     * 同步写入 Baseon 登录上下文，供其日志、审计与 {@code SecurityUtil} 读取。
     *
     * <p>Daos 使用 ULID 与字符串租户，写入 Baseon 的字符串标识字段
     * {@code subjectId} / {@code tenantKey}；权限是应用级语义，不塞进全局权限集合，
     * 由 {@link DaosPermissionChecker} 按应用维度裁决。</p>
     */
    private static void bindBaseonLoginUser(Session session) {
        LoginUser user = new LoginUser();
        user.setUsername(session.username());
        user.setSubjectId(session.subjectId());
        user.setTenantKey(session.tenantId());
        user.setPermissions(Set.of());
        SecurityUtil.set(user);
    }

    private static String servletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isEmpty() ? uri : uri.substring(contextPath.length());
    }
}
