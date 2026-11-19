package io.github.ayongbox.daos.runtime.menu;

import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.menu.SchemaMenu;
import io.github.ayongbox.daos.core.metadata.view.SchemaView;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
                        targetCode(snapshot, menu),
                        formCode(snapshot, menu),
                        layoutOf(menu)))
                .toList();
    }

    /** 页面布局存放在菜单 ext.layout，运行时原样返回。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> layoutOf(SchemaMenu menu) {
        Object layout = menu.ext().get("layout");
        if (layout instanceof Map<?, ?> map && !map.isEmpty()) {
            return (Map<String, Object>) map;
        }
        return null;
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

    /**
     * 菜单绑定的表单编码：优先 {@code ext.formId}，其次按目标表单或目标视图所属表推导。
     */
    private static String formCode(SchemaSnapshot snapshot, SchemaMenu menu) {
        Object bound = menu.ext().get("formId");
        if (bound instanceof String formId && !formId.isBlank()) {
            String code = snapshot.app().forms().stream()
                    .filter(form -> !form.deleted() && form.id().equals(formId))
                    .map(SchemaForm::code)
                    .findFirst()
                    .orElse(null);
            if (code != null) {
                return code;
            }
        }
        return switch (menu.menuType()) {
            case FORM -> snapshot.app().forms().stream()
                    .filter(form -> !form.deleted() && form.id().equals(menu.targetId()))
                    .map(SchemaForm::code)
                    .findFirst()
                    .orElse(null);
            case VIEW -> firstFormCodeOfTable(snapshot, viewTableId(snapshot, menu.targetId()));
            case TABLE -> firstFormCodeOfTable(snapshot, menu.targetId());
        };
    }

    private static String viewTableId(SchemaSnapshot snapshot, String viewId) {
        return snapshot.app().views().stream()
                .filter(view -> view.id().equals(viewId))
                .map(SchemaView::tableId)
                .findFirst()
                .orElse(null);
    }

    private static String firstFormCodeOfTable(SchemaSnapshot snapshot, String tableId) {
        if (tableId == null) {
            return null;
        }
        return snapshot.app().forms().stream()
                .filter(form -> !form.deleted() && form.tableId().equals(tableId))
                .map(SchemaForm::code)
                .findFirst()
                .orElse(null);
    }

    public record MenuSummary(
            String menuId,
            String menuCode,
            String displayName,
            String icon,
            String menuType,
            String targetId,
            String targetCode,
            String formCode,
            Map<String, Object> layout) {

        public MenuSummary(
                String menuId,
                String menuCode,
                String displayName,
                String icon,
                String menuType,
                String targetId,
                String targetCode) {
            this(menuId, menuCode, displayName, icon, menuType, targetId, targetCode, null, null);
        }
    }
}
