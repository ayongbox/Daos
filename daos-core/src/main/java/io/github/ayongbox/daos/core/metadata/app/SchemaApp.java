package io.github.ayongbox.daos.core.metadata.app;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.menu.MenuType;
import io.github.ayongbox.daos.core.metadata.menu.SchemaMenu;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.view.SchemaView;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 应用元数据。发布单元是应用，表、表单、权限是应用的内部结构。
 *
 * <p>本类型是草稿与快照共用的聚合根：草稿可反复覆盖，快照一经生成即不可变。
 * 跨应用共享只能通过表上的 {@code ExternalTableRef} 做只读引用，不允许重复持有。</p>
 */
public record SchemaApp(
        String id,
        String code,
        String displayName,
        String description,
        String icon,
        MetadataStatus status,
        List<SchemaTable> tables,
        List<SchemaForm> forms,
        List<SchemaView> views,
        List<SchemaMenu> menus,
        List<SchemaPermission> permissions,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    public static final int MAX_TABLES = 500;
    public static final int MAX_EXTERNAL_REFS = 50;

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    public SchemaApp {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(displayName, "displayName");

        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("应用编码不合法: " + code + "，要求 [a-z][a-z0-9_]{0,31}");
        }

        status = status == null ? MetadataStatus.DRAFT : status;
        tables = tables == null ? List.of() : List.copyOf(tables);
        forms = forms == null ? List.of() : List.copyOf(forms);
        views = views == null ? List.of() : List.copyOf(views);
        menus = menus == null ? List.of() : List.copyOf(menus);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;

        if (tables.size() > MAX_TABLES) {
            throw new IllegalArgumentException("应用 " + code + " 的表数超过上限 " + MAX_TABLES);
        }
        // 紧凑构造器中字段尚未赋值，校验必须显式使用参数。
        validateTables(id, code, tables);
        validateForms(id, code, tables, forms);
        validateViews(id, code, tables, views);
        validateMenus(id, code, tables, forms, views, menus);
        validatePermissions(id, code, permissions);
    }

    /**
     * 兼容尚未包含视图与菜单的调用方：按空列表处理。
     */
    public SchemaApp(
            String id,
            String code,
            String displayName,
            String description,
            String icon,
            MetadataStatus status,
            List<SchemaTable> tables,
            List<SchemaForm> forms,
            List<SchemaPermission> permissions,
            Map<String, Object> ext,
            String createdBy,
            String createdAt,
            String updatedBy,
            String updatedAt,
            boolean deleted,
            int schemaVersion) {
        this(id, code, displayName, description, icon, status, tables, forms,
                List.of(), List.of(), permissions, ext, createdBy, createdAt,
                updatedBy, updatedAt, deleted, schemaVersion);
    }

    private static void validateTables(String id, String code, List<SchemaTable> tables) {
        Set<String> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        int externalRefs = 0;

        for (SchemaTable table : tables) {
            if (!Objects.equals(id, table.appId())) {
                throw new IllegalArgumentException(
                        "表 " + table.code() + " 的 appId 与应用不一致: " + table.appId());
            }
            if (!ids.add(table.id())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复表 id: " + table.id());
            }
            if (!codes.add(table.code())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复表编码: " + table.code());
            }
            if (table.externalRef() != null) {
                externalRefs++;
            }
        }

        if (externalRefs > MAX_EXTERNAL_REFS) {
            throw new IllegalArgumentException(
                    "应用 " + code + " 的外部引用数超过上限 " + MAX_EXTERNAL_REFS + "，当前 " + externalRefs);
        }
    }

    private static void validateForms(
            String id, String code, List<SchemaTable> tables, List<SchemaForm> forms) {
        Set<String> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();

        for (SchemaForm form : forms) {
            if (!Objects.equals(id, form.appId())) {
                throw new IllegalArgumentException(
                        "表单 " + form.code() + " 的 appId 与应用不一致: " + form.appId());
            }
            if (!ids.add(form.id())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复表单 id: " + form.id());
            }
            if (!codes.add(form.code())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复表单编码: " + form.code());
            }
            if (tables.stream().noneMatch(table -> table.id().equals(form.tableId()))) {
                throw new IllegalArgumentException(
                        "表单 " + form.code() + " 指向不存在的表: " + form.tableId());
            }
        }
    }

    private static void validateViews(
            String id, String code, List<SchemaTable> tables, List<SchemaView> views) {
        Set<String> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();

        for (SchemaView view : views) {
            if (!Objects.equals(id, view.appId())) {
                throw new IllegalArgumentException(
                        "视图 " + view.code() + " 的 appId 与应用不一致: " + view.appId());
            }
            if (!CODE_PATTERN.matcher(view.code()).matches()) {
                throw new IllegalArgumentException(
                        "视图编码不合法: " + view.code() + "，要求 [a-z][a-z0-9_]{0,31}");
            }
            if (!ids.add(view.id())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复视图 id: " + view.id());
            }
            if (!codes.add(view.code())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复视图编码: " + view.code());
            }

            SchemaTable table = tables.stream()
                    .filter(item -> item.id().equals(view.tableId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "视图 " + view.code() + " 指向不存在的表: " + view.tableId()));

            Set<String> columnFieldIds = new HashSet<>();
            view.columns().forEach(column -> {
                if (!columnFieldIds.add(column.fieldId())) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 存在重复列字段: " + column.fieldId());
                }
                SchemaField field = requireField(table, view.code(), column.fieldId());
                if (field.logicalType().computed()) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 的列不能是派生字段: " + field.code());
                }
            });
            view.filters().forEach(filter ->
                    requireField(table, view.code(), filter.fieldId()));
            view.sorts().forEach(sort -> {
                if (sort.aggregation() != null) {
                    return;
                }
                requireField(table, view.code(), sort.fieldId());
            });

            Set<String> groupFieldIds = new HashSet<>();
            Set<String> groupCodes = new HashSet<>();
            for (String fieldId : view.groupBy()) {
                if (!groupFieldIds.add(fieldId)) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 存在重复分组字段: " + fieldId);
                }
                SchemaField field = requireField(table, view.code(), fieldId);
                if (field.logicalType().computed()) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 的分组字段不能是派生字段: " + field.code());
                }
                groupCodes.add(field.code());
            }

            Set<String> displayNames = new HashSet<>();
            view.aggregations().forEach(aggregate -> {
                if (!displayNames.add(aggregate.displayName())) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 存在重复汇总列名: " + aggregate.displayName());
                }
                if (groupCodes.contains(aggregate.displayName())) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 的汇总列名与分组字段冲突: "
                                    + aggregate.displayName());
                }
                if (aggregate.fieldId() == null) {
                    return;
                }
                SchemaField field = requireField(table, view.code(), aggregate.fieldId());
                if (field.logicalType().computed()) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 的汇总字段不能是派生字段: " + field.code());
                }
                if (aggregate.function().requiresNumericField()
                        && !field.logicalType().numeric()) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 的字段不支持数值汇总 "
                                    + aggregate.function().code() + ": " + field.code());
                }
            });

            view.sorts().forEach(sort -> {
                if (sort.aggregation() != null && !displayNames.contains(sort.aggregation())) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 的排序引用了不存在的汇总列: "
                                    + sort.aggregation());
                }
            });
            if (!view.having().isEmpty() && view.aggregations().isEmpty()) {
                throw new IllegalArgumentException(
                        "视图 " + view.code() + " 的 HAVING 条件必须配合汇总列使用");
            }
            view.having().forEach(condition -> {
                if (!displayNames.contains(condition.aggregation())) {
                    throw new IllegalArgumentException(
                            "视图 " + view.code() + " 的 HAVING 引用了不存在的汇总列: "
                                    + condition.aggregation());
                }
            });
        }
    }

    private static void validateMenus(
            String id,
            String code,
            List<SchemaTable> tables,
            List<SchemaForm> forms,
            List<SchemaView> views,
            List<SchemaMenu> menus) {
        Set<String> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        Set<String> tableIds = tables.stream().map(SchemaTable::id).collect(HashSet::new, Set::add, Set::addAll);
        Set<String> formIds = forms.stream().map(SchemaForm::id).collect(HashSet::new, Set::add, Set::addAll);
        Set<String> viewIds = views.stream().map(SchemaView::id).collect(HashSet::new, Set::add, Set::addAll);

        for (SchemaMenu menu : menus) {
            if (!Objects.equals(id, menu.appId())) {
                throw new IllegalArgumentException(
                        "菜单 " + menu.code() + " 的 appId 与应用不一致: " + menu.appId());
            }
            if (!CODE_PATTERN.matcher(menu.code()).matches()) {
                throw new IllegalArgumentException(
                        "菜单编码不合法: " + menu.code() + "，要求 [a-z][a-z0-9_]{0,31}");
            }
            if (!ids.add(menu.id())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复菜单 id: " + menu.id());
            }
            if (!codes.add(menu.code())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复菜单编码: " + menu.code());
            }

            boolean targetExists = switch (menu.menuType()) {
                case VIEW -> viewIds.contains(menu.targetId());
                case FORM -> formIds.contains(menu.targetId());
                case TABLE -> tableIds.contains(menu.targetId());
            };
            if (!targetExists) {
                throw new IllegalArgumentException(
                        "菜单 " + menu.code() + " 指向不存在的" + menu.menuType() + ": " + menu.targetId());
            }
        }
    }

    private static SchemaField requireField(
            SchemaTable table, String viewCode, String fieldId) {
        return table.fields().stream()
                .filter(field -> field.id().equals(fieldId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "视图 " + viewCode + " 引用了表 " + table.code() + " 中不存在的字段: " + fieldId));
    }

    private static void validatePermissions(
            String id, String code, List<SchemaPermission> permissions) {
        Set<String> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();

        for (SchemaPermission permission : permissions) {
            if (!Objects.equals(id, permission.appId())) {
                throw new IllegalArgumentException(
                        "权限 " + permission.code() + " 的 appId 与应用不一致: " + permission.appId());
            }
            if (!ids.add(permission.id())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复权限 id: " + permission.id());
            }
            if (!codes.add(permission.code())) {
                throw new IllegalArgumentException("应用 " + code + " 存在重复权限编码: " + permission.code());
            }
        }
    }

    public SchemaTable requireTable(String tableId) {
        return tables.stream()
                .filter(table -> table.id().equals(tableId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("应用 " + code + " 不存在表: " + tableId));
    }
}
