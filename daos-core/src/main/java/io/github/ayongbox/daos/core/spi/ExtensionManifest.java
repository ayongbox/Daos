package io.github.ayongbox.daos.core.spi;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 扩展包元数据。描述一个商业扩展包提供哪些扩展点、依赖哪些扩展包，以及与内核契约的兼容版本。
 *
 * <p>扩展包只通过 {@link ExtensionPoint} 接入，不依赖 runtime；未安装任何扩展包时社区版照常启动。
 * 扩展包以 Spring Bean 形式提供本记录，由内核在启动时统一装配。</p>
 */
public record ExtensionManifest(
        String code,
        String name,
        String version,
        String vendor,
        int contractVersion,
        List<String> requires,
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
        if (contractVersion < 1) {
            throw new IllegalArgumentException("扩展包 " + code + " 的契约版本必须大于 0");
        }

        requires = requires == null ? List.of() : List.copyOf(requires);
        for (String required : requires) {
            if (required == null || !CODE.matcher(required).matches()) {
                throw new IllegalArgumentException(
                        "扩展包 " + code + " 的依赖编码不合法: " + required);
            }
            if (required.equals(code)) {
                throw new IllegalArgumentException("扩展包不能依赖自身: " + code);
            }
        }
        if (new HashSet<>(requires).size() != requires.size()) {
            throw new IllegalArgumentException("扩展包 " + code + " 存在重复依赖");
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
