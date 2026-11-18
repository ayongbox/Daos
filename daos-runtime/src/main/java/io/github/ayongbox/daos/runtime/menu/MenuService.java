package io.github.ayongbox.daos.runtime.menu;

import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.menu.SchemaMenu;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 应用菜单运行时。菜单随应用发布进入快照，运行时按排序返回可见项。
 */
public final class MenuService {

    private final SnapshotStore snapshotStore;

    public MenuService(SnapshotStore snapshotStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
    }

    public List<MenuSummary> listMenus(String appId) {
        SchemaSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用还没有生效版本: " + appId));

        return snapshot.app().menus().stream()
                .filter(menu -> !menu.deleted())
                .filter(SchemaMenu::visible)
                .filter(menu -> menu.status() != MetadataStatus.DISABLED)
                .sorted(Comparator.comparingInt(SchemaMenu::sortOrder))
                .map(menu -> new MenuSummary(
                        menu.id(),
                        menu.code(),
                        menu.displayName(),
                        menu.icon(),
                        menu.menuType().name(),
                        menu.targetId(),
                        targetCode(snapshot, menu)))
                .toList();
    }

    private static String targetCode(SchemaSnapshot snapshot, SchemaMenu menu) {
        return switch (menu.menuType()) {
            case VIEW -> snapshot.app().views().stream()
                    .filter(view -> view.id().equals(menu.targetId()))
                    .map(view -> view.code())
                    .findFirst()
                    .orElse(null);
            case FORM -> snapshot.app().forms().stream()
                    .filter(form -> form.id().equals(menu.targetId()))
                    .map(form -> form.code())
                    .findFirst()
                    .orElse(null);
            case TABLE -> snapshot.app().tables().stream()
                    .filter(table -> table.id().equals(menu.targetId()))
                    .map(table -> table.code())
                    .findFirst()
                    .orElse(null);
        };
    }

    public record MenuSummary(
            String menuId,
            String menuCode,
            String displayName,
            String icon,
            String menuType,
            String targetId,
            String targetCode) {
    }
}
