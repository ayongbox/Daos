package io.github.ayongbox.daos.core.metadata.dictionary;

import io.github.ayongbox.daos.core.metadata.common.AuditInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 数据字典。字典属于应用，字段通过 {@code dictCode} 引用。
 *
 * <p>字典项编码在字典内唯一且发布后不可修改；树形字典要求父项存在且不允许成环。</p>
 */
public record SchemaDictionary(
        String id,
        String appId,
        String code,
        String name,
        String description,
        boolean tree,
        boolean enabled,
        List<DictionaryItem> items,
        Map<String, Object> ext,
        String createdBy,
        String createdAt,
        String updatedBy,
        String updatedAt,
        boolean deleted,
        int schemaVersion) implements AuditInfo {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    public SchemaDictionary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("字典编码不合法: " + code + "，要求 [a-z][a-z0-9_]{0,63}");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("字典名称不能为空");
        }

        items = items == null ? List.of() : List.copyOf(items);
        validateItems(code, tree, items);
        ext = ext == null ? Map.of() : Map.copyOf(ext);
        schemaVersion = schemaVersion == 0 ? 1 : schemaVersion;
    }

    public DictionaryItem itemByCode(String itemCode) {
        return items.stream()
                .filter(item -> item.code().equals(itemCode))
                .findFirst()
                .orElse(null);
    }

    private static void validateItems(String dictCode, boolean tree, List<DictionaryItem> items) {
        Map<String, DictionaryItem> byId = new HashMap<>();
        Set<String> codes = new HashSet<>();

        for (DictionaryItem item : items) {
            if (byId.putIfAbsent(item.id(), item) != null) {
                throw new IllegalArgumentException(
                        "字典 " + dictCode + " 存在重复字典项 id: " + item.id());
            }
            if (!codes.add(item.code())) {
                throw new IllegalArgumentException(
                        "字典 " + dictCode + " 存在重复字典项编码: " + item.code());
            }
        }

        for (DictionaryItem item : items) {
            String parentId = item.parentId();
            if (parentId == null) {
                continue;
            }
            if (!tree) {
                throw new IllegalArgumentException(
                        "平铺字典 " + dictCode + " 的字典项不允许设置 parentId: " + item.code());
            }
            if (parentId.equals(item.id())) {
                throw new IllegalArgumentException(
                        "字典项不能以自身为父节点: " + item.code());
            }
            if (!byId.containsKey(parentId)) {
                throw new IllegalArgumentException(
                        "字典项 " + item.code() + " 的父项不存在: " + parentId);
            }
        }

        if (tree) {
            rejectCycles(dictCode, byId);
        }
    }

    private static void rejectCycles(String dictCode, Map<String, DictionaryItem> byId) {
        for (DictionaryItem item : byId.values()) {
            Set<String> visited = new HashSet<>();
            String current = item.id();
            while (current != null) {
                if (!visited.add(current)) {
                    throw new IllegalArgumentException(
                            "字典 " + dictCode + " 的字典项存在循环引用: " + item.code());
                }
                DictionaryItem node = byId.get(current);
                current = node == null ? null : node.parentId();
            }
        }
    }
}
