package io.github.ayongbox.daos.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 控制台与运行时统一启动入口。运行时前端产物由网关按路由分发。
 */
@SpringBootApplication(scanBasePackages = "io.github.ayongbox.daos")
public class DaosApplication {

    public static void main(String[] args) {
        SpringApplication.run(DaosApplication.class, args);
    }
}
