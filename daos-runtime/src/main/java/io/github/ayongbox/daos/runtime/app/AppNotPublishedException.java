package io.github.ayongbox.daos.runtime.app;

/**
 * 应用还没有可用的生效快照，运行期资源视为不存在。
 */
public class AppNotPublishedException extends RuntimeException {

    public AppNotPublishedException(String message) {
        super(message);
    }
}
