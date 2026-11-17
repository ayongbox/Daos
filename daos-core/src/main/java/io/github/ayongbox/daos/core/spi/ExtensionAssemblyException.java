package io.github.ayongbox.daos.core.spi;

/**
 * 扩展装配失败。装配是原子的：任一扩展包校验失败时不改变已安装状态。
 */
public class ExtensionAssemblyException extends IllegalArgumentException {

    public ExtensionAssemblyException(String message) {
        super(message);
    }
}
