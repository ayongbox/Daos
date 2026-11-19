package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.spi.ExtensionManifest;
import io.github.ayongbox.daos.core.spi.ExtensionPoint;
import io.github.ayongbox.daos.core.spi.ExtensionRegistry;
import io.github.ayongbox.daos.web.config.ExtensionConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * F00-06 装配入口回归：社区版无扩展时可独立启动，扩展包以 Bean 形式接入。
 */
class ExtensionConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ExtensionConfiguration.class);

    @Test
    void communityStartsWithEmptyRegistry() {
        runner.run(context -> {
            assertNull(context.getStartupFailure(), "无扩展时内核应正常启动");
            assertEquals(List.of(), context.getBean(ExtensionRegistry.class).installedCodes());
        });
    }

    @Test
    void installsManifestBeans() {
        runner
                .withBean("baseManifest", ExtensionManifest.class,
                        () -> manifest("ext.base", List.of(), "base"))
                .withBean("plusManifest", ExtensionManifest.class,
                        () -> manifest("ext.plus", List.of("ext.base"), "plus"))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    ExtensionRegistry registry = context.getBean(ExtensionRegistry.class);
                    assertEquals(2, registry.manifests().size());
                    assertEquals(
                            java.util.Set.of("ext.base", "ext.plus"),
                            java.util.Set.copyOf(registry.installedCodes()));
                });
    }

    @Test
    void incompatibleContractVersionFailsStartup() {
        runner
                .withBean("legacyManifest", ExtensionManifest.class,
                        () -> new ExtensionManifest(
                                "ext.legacy", "legacy", "1.0.0", "daos",
                                ExtensionRegistry.CURRENT_CONTRACT_VERSION + 1,
                                List.of(), List.of(point("legacy"))))
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    private static ExtensionManifest manifest(
            String code, List<String> requires, String pointCode) {
        return new ExtensionManifest(
                code, code, "1.0.0", "daos",
                ExtensionRegistry.CURRENT_CONTRACT_VERSION, requires, List.of(point(pointCode)));
    }

    private static ExtensionPoint point(String code) {
        return new ExtensionPoint() {
            @Override
            public String code() {
                return code;
            }

            @Override
            public String version() {
                return "1.0.0";
            }
        };
    }
}
