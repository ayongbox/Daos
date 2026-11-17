package io.github.ayongbox.daos.core.metadata.form;

/**
 * 事件处理器类型。SCRIPT 与 PLUGIN 由商业扩展提供沙箱与注册中心。
 */
public enum HandlerType {
    SCRIPT,
    FORMULA,
    WEBHOOK,
    PLUGIN
}
