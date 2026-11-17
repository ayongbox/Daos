package io.github.ayongbox.daos.runtime.audit;

/**
 * 当前请求的链路 id 来源。由 Web 层注入，运行时模块不直接依赖追踪框架。
 */
@FunctionalInterface
public interface TraceIdSupplier {

    String currentTraceId();
}
