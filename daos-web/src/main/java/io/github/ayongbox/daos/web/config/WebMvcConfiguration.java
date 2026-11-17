package io.github.ayongbox.daos.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ayongbox.daos.runtime.auth.AuthenticationService;
import io.github.ayongbox.daos.web.auth.SessionAuthInterceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层装配。所有 {@code /api/**} 接口默认要求登录，登录接口由拦截器内部放行。
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    public WebMvcConfiguration(AuthenticationService authenticationService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SessionAuthInterceptor(authenticationService, objectMapper))
                .addPathPatterns("/api/**");
    }
}
