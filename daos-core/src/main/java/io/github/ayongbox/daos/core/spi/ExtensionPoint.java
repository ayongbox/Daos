package io.github.ayongbox.daos.core.spi;

/**
 * 商业扩展装配契约。社区版无任何扩展时可独立启动。
 */
public interface ExtensionPoint {

    String code();

    String version();

    /**
     * 声明兼容的道生契约主版本，不匹配时拒绝装配。
     */
    default int compatibleSchemaVersion() {
        return 1;
    }
}
