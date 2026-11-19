package io.github.ayongbox.daos.core.spi;

/**
 * 商业扩展装配契约。社区版无任何扩展时可独立启动。
 *
 * <p>契约兼容版本由所属 {@link ExtensionManifest#contractVersion()} 统一声明，
 * 扩展点本身只描述能力编码与实现版本。</p>
 */
public interface ExtensionPoint {

    String code();

    String version();
}
