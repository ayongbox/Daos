package io.github.ayongbox.daos.runtime.engine.query;

/**
 * 查询编译失败。携带具体字段与原因，由 Web 层映射为 400，禁止静默丢弃条件。
 */
public class QueryCompileException extends IllegalArgumentException {

    public QueryCompileException(String message) {
        super(message);
    }
}
