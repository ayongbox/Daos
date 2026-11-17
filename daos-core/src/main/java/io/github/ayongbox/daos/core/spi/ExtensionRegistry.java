package io.github.ayongbox.daos.core.spi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 扩展点注册表。社区版默认不安装任何扩展包，内核能力不依赖扩展。
 *
 * <p>装配规则：扩展包编码与扩展点编码全局唯一；扩展点声明的契约主版本必须与内核一致。
 * 批量装配是全有或全无，失败时不留下半装配状态。</p>
 */
public final class ExtensionRegistry {

    /**
     * 内核当前契约主版本。扩展点必须声明同一主版本才允许装配。
     */
    public static final int CURRENT_CONTRACT_VERSION = 1;

    private final Map<String, ExtensionManifest> manifests = new LinkedHashMap<>();
    private final Map<String, ExtensionPoint> points = new LinkedHashMap<>();

    /**
     * 社区版默认注册表：不含任何扩展包。
     */
    public static ExtensionRegistry empty() {
        return new ExtensionRegistry();
    }

    public void install(ExtensionManifest manifest) {
        installAll(List.of(manifest));
    }

    /**
     * 批量装配。先校验全部扩展包，再统一写入，任一失败不改变现有状态。
     */
    public void installAll(List<ExtensionManifest> candidates) {
        Objects.requireNonNull(candidates, "candidates");

        Map<String, ExtensionManifest> nextManifests = new LinkedHashMap<>(manifests);
        Map<String, ExtensionPoint> nextPoints = new LinkedHashMap<>(points);

        for (ExtensionManifest manifest : candidates) {
            Objects.requireNonNull(manifest, "manifest");
            if (nextManifests.putIfAbsent(manifest.code(), manifest) != null) {
                throw new ExtensionAssemblyException("扩展包已安装: " + manifest.code());
            }
            for (ExtensionPoint point : manifest.points()) {
                if (point.compatibleSchemaVersion() != CURRENT_CONTRACT_VERSION) {
                    throw new ExtensionAssemblyException(
                            "扩展点 " + point.code() + " 声明契约版本 "
                                    + point.compatibleSchemaVersion() + "，内核为 " + CURRENT_CONTRACT_VERSION);
                }
                if (nextPoints.putIfAbsent(point.code(), point) != null) {
                    throw new ExtensionAssemblyException("扩展点编码重复: " + point.code());
                }
            }
        }

        manifests.clear();
        manifests.putAll(nextManifests);
        points.clear();
        points.putAll(nextPoints);
    }

    public boolean isInstalled(String packageCode) {
        return manifests.containsKey(packageCode);
    }

    public List<ExtensionManifest> manifests() {
        return List.copyOf(manifests.values());
    }

    public List<ExtensionPoint> points() {
        return List.copyOf(points.values());
    }

    public Optional<ExtensionPoint> findPoint(String code) {
        return Optional.ofNullable(points.get(code));
    }

    /**
     * 已安装扩展包编码，按安装顺序返回，便于日志与诊断。
     */
    public List<String> installedCodes() {
        return List.copyOf(manifests.keySet());
    }
}
