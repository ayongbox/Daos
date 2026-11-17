package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.spi.ExtensionAssemblyException;
import io.github.ayongbox.daos.core.spi.ExtensionManifest;
import io.github.ayongbox.daos.core.spi.ExtensionPoint;
import io.github.ayongbox.daos.core.spi.ExtensionRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionRegistryTest {

    @Test
    void communityRegistryStartsEmpty() {
        ExtensionRegistry registry = ExtensionRegistry.empty();

        assertTrue(registry.manifests().isEmpty());
        assertTrue(registry.points().isEmpty());
        assertTrue(registry.findPoint("multi_tenant").isEmpty());
    }

    @Test
    void installsExtensionAndExposesPoints() {
        ExtensionRegistry registry = ExtensionRegistry.empty();
        ExtensionManifest manifest = manifest("ext.multi_tenant",
                new TestPoint("multi_tenant", "1.0.0", ExtensionRegistry.CURRENT_CONTRACT_VERSION));

        registry.install(manifest);

        assertTrue(registry.isInstalled("ext.multi_tenant"));
        assertEquals(List.of("ext.multi_tenant"), registry.installedCodes());
        assertTrue(registry.findPoint("multi_tenant").isPresent());
    }

    @Test
    void rejectsDuplicatePackageCode() {
        ExtensionRegistry registry = ExtensionRegistry.empty();
        registry.install(manifest("ext.a", point("a")));

        ExtensionAssemblyException error = assertThrows(ExtensionAssemblyException.class,
                () -> registry.install(manifest("ext.a", point("b"))));

        assertTrue(error.getMessage().contains("已安装"));
    }

    @Test
    void rejectsDuplicatePointAcrossPackages() {
        ExtensionRegistry registry = ExtensionRegistry.empty();
        registry.install(manifest("ext.a", point("shared")));

        ExtensionAssemblyException error = assertThrows(ExtensionAssemblyException.class,
                () -> registry.install(manifest("ext.b", point("shared"))));

        assertTrue(error.getMessage().contains("重复"));
    }

    @Test
    void rejectsIncompatibleContractVersion() {
        ExtensionRegistry registry = ExtensionRegistry.empty();
        ExtensionPoint legacy = new TestPoint("legacy", "1.0.0", ExtensionRegistry.CURRENT_CONTRACT_VERSION + 1);

        ExtensionAssemblyException error = assertThrows(ExtensionAssemblyException.class,
                () -> registry.install(manifest("ext.legacy", legacy)));

        assertTrue(error.getMessage().contains("契约版本"));
        assertTrue(registry.manifests().isEmpty());
    }

    @Test
    void batchInstallIsAtomic() {
        ExtensionRegistry registry = ExtensionRegistry.empty();
        ExtensionManifest valid = manifest("ext.a", point("a"));
        ExtensionManifest conflict = manifest("ext.b", point("a"));

        assertThrows(ExtensionAssemblyException.class,
                () -> registry.installAll(List.of(valid, conflict)));

        assertFalse(registry.isInstalled("ext.a"));
        assertTrue(registry.points().isEmpty());
    }

    @Test
    void rejectsManifestWithoutPoints() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExtensionManifest("ext.empty", "空扩展", "1.0.0", null, List.of()));
    }

    private static ExtensionManifest manifest(String code, ExtensionPoint... points) {
        return new ExtensionManifest(code, code, "1.0.0", "daos", List.of(points));
    }

    private static ExtensionPoint point(String code) {
        return new TestPoint(code, "1.0.0", ExtensionRegistry.CURRENT_CONTRACT_VERSION);
    }

    private record TestPoint(String code, String version, int compatibleSchemaVersion) implements ExtensionPoint {
    }
}
