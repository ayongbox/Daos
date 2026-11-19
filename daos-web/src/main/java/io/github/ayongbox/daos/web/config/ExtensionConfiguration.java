package io.github.ayongbox.daos.web.config;

import io.github.ayongbox.daos.core.spi.ExtensionManifest;
import io.github.ayongbox.daos.core.spi.ExtensionRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 扩展包装配入口。
 *
 * <p>商业扩展包以 {@link ExtensionManifest} Bean 的形式提供元数据，内核在启动时统一校验并装配。
 * 没有任何扩展包 Bean 时返回空注册表，社区版照常启动；契约版本不匹配或依赖缺失时启动失败，
 * 不进入半装配状态。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ExtensionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ExtensionConfiguration.class);

    @Bean
    public ExtensionRegistry extensionRegistry(ObjectProvider<ExtensionManifest> manifests) {
        List<ExtensionManifest> candidates = manifests.orderedStream().toList();
        ExtensionRegistry registry = ExtensionRegistry.empty();
        if (candidates.isEmpty()) {
            log.info("未检测到扩展包，按社区版能力启动");
            return registry;
        }
        registry.installAll(candidates);
        log.info("已装配扩展包: {}", registry.installedCodes());
        return registry;
    }
}
