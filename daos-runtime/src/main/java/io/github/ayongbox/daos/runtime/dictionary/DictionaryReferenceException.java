package io.github.ayongbox.daos.runtime.dictionary;

/**
 * 字典引用校验失败。发布校验阶段应把该异常直接返回给设计器，不允许带病发布。
 */
public class DictionaryReferenceException extends IllegalArgumentException {

    public DictionaryReferenceException(String message) {
        super(message);
    }
}
