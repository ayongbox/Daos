package io.github.ayongbox.daos.core.spi;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 扩展包元数据。描述一个商业扩展包提供哪些扩展点，以及自身的版本信息。
 *
 * <p>扩展包只通过 {@link ExtensionPoint} 接入，不依赖 runtime；未安装任何扩展包时社区版照常启动。</p>
 */
public record ExtensionManifest(
        String code,
        String name,
        String version,
        String vendor,
        List<ExtensionPoint> points) {

    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_.-]{1,63}");

    public ExtensionManifest {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(version, "version");
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "扩展包编码不合法: " + code + "，要求 [a-z][a-z0-9_.-]{1,63}");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("扩展包名称不能为空");
        }
        if (version.isBlank()) {
            throw new IllegalArgumentException("扩展包版本不能为空");
        }
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("扩展包 " + code + " 至少需要一个扩展点");
        }
        points = List.copyOf(points);

        Set<String> pointCodes = new HashSet<>();
        for (ExtensionPoint point : points) {
            if (!pointCodes.add(point.code())) {
                throw new IllegalArgumentException(
                        "扩展包 " + code + " 存在重复扩展点: " + point.code());
            }
        }
    }
}
