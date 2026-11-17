package io.github.ayongbox.daos.runtime.exec;

/**
 * 写入冲突。记录不存在、超出数据范围或乐观锁版本不一致时抛出。
 */
public class WriteConflictException extends RuntimeException {

    public WriteConflictException(String message) {
        super(message);
    }
}
