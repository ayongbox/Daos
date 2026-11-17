package io.github.ayongbox.daos.runtime.engine;

/**
 * 归一化失败。携带失败原因，由 Web 层映射为 400 与结构化错误项。
 */
public class SchemaNormalizeException extends IllegalArgumentException {

    public SchemaNormalizeException(String message) {
        super(message);
    }
}
