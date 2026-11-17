package io.github.ayongbox.daos.runtime.engine;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 快照内容哈希。对应用元数据做规范序列化后计算 SHA-256，用于快照比对与幂等发布。
 *
 * <p>规范化规则：对象字段名升序、数组元素按自身规范串升序，因此 Map 与集合的插入顺序
 * 不影响结果；审计字段不参与计算，避免仅因保存时间变化产生新快照。</p>
 */
public final class SchemaContentHasher {

    private final ObjectMapper mapper;

    public SchemaContentHasher() {
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        mapper.addMixIn(SchemaApp.class, AuditMixIn.class);
        mapper.addMixIn(SchemaTable.class, AuditMixIn.class);
        mapper.addMixIn(SchemaField.class, AuditMixIn.class);
        mapper.addMixIn(SchemaForm.class, AuditMixIn.class);
        mapper.addMixIn(SchemaPermission.class, AuditMixIn.class);
    }

    public String hash(SchemaApp app) {
        Objects.requireNonNull(app, "app");
        JsonNode tree = mapper.valueToTree(app);
        return sha256(canonical(tree));
    }

    private String canonical(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        appendCanonical(node, builder);
        return builder.toString();
    }

    private void appendCanonical(JsonNode node, StringBuilder builder) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            for (Iterator<Map.Entry<String, JsonNode>> iterator = node.fields(); iterator.hasNext(); ) {
                names.add(iterator.next().getKey());
            }
            Collections.sort(names);

            builder.append('{');
            for (int index = 0; index < names.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                String name = names.get(index);
                builder.append(TextNode.valueOf(name)).append(':');
                appendCanonical(node.get(name), builder);
            }
            builder.append('}');
            return;
        }

        if (node.isArray()) {
            List<String> items = new ArrayList<>(node.size());
            node.forEach(item -> items.add(canonical(item)));
            Collections.sort(items);

            builder.append('[');
            builder.append(String.join(",", items));
            builder.append(']');
            return;
        }

        builder.append(node);
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", error);
        }
    }

    /**
     * 审计字段不参与内容哈希：同一份结构重复保存不应产生新快照。
     */
    private abstract static class AuditMixIn {

        @JsonIgnore
        public abstract String createdBy();

        @JsonIgnore
        public abstract String createdAt();

        @JsonIgnore
        public abstract String updatedBy();

        @JsonIgnore
        public abstract String updatedAt();
    }
}
