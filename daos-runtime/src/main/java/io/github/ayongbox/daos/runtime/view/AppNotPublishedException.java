package io.github.ayongbox.daos.runtime.view;

/**
 * 应用还没有可用的生效快照，视图相关内容一律视为不存在。
 */
public class AppNotPublishedException extends RuntimeException {

    public AppNotPublishedException(String message) {
        super(message);
    }
}
