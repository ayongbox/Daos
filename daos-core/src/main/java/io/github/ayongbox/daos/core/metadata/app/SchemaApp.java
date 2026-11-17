package io.github.ayongbox.daos.core.metadata.app;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;

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
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;

        if (tables.size() > MAX_TABLES) {
            throw new IllegalArgumentException("应用 " + code + " 的表数超过上限 " + MAX_TABLES);
        }
        // 紧凑构造器中字段尚未赋值，校验必须显式使用参数。
        validateTables(id, code, tables);
        validateForms(id, code, tables, forms);
        validatePermissions(id, code, permissions);
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
