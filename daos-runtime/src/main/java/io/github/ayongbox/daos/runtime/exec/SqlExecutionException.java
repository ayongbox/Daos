package io.github.ayongbox.daos.runtime.exec;

/**
 * SQL 执行失败。保留原始 SQL 与底层异常，由上层决定是否暴露细节。
 */
public class SqlExecutionException extends RuntimeException {

    public SqlExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
