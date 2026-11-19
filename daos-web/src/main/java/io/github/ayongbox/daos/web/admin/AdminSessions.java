package io.github.ayongbox.daos.web.admin;

import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 管理端控制器的会话读取。未登录请求已被拦截器拒绝，这里只做兜底。
 */
final class AdminSessions {

    private AdminSessions() {
    }

    static Session session(HttpServletRequest request) {
        Object value = request.getAttribute(SessionAuthInterceptor.SESSION_ATTRIBUTE);
        if (value instanceof Session session) {
            return session;
        }
        throw new IllegalStateException("未登录或会话已过期");
    }

    /**
     * 操作者标识：优先组织成员 id，纯账号会话回退到账号 id。
     */
    static String operator(Session session) {
        return session.memberId() == null || session.memberId().isBlank()
                ? session.subjectId()
                : session.memberId();
    }
}
