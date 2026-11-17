package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MaskType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.metadata.permission.MaskRule;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.permission.FieldMasker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FieldMaskerTest {

    private static final String APP_ID = "app-1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private final FieldMasker masker = new FieldMasker();
    private IrTable table;

    @BeforeEach
    void setUp() {
        SchemaTable users = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(),
                List.of(
                        field("f1", "phone"),
                        field("f2", "email"),
                        field("f3", "name"),
                        field("f4", "id_card"),
                        field("f5", "remark")),
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
        table = IrTable.of(users, "app_demo_users");
    }

    @Test
    void masksPhone() {
        Map<String, Object> row = mask(maskRule("f1", MaskType.PHONE, null, null), "13800138000");

        assertEquals("138****8000", row.get("phone"));
    }

    @Test
    void masksEmail() {
        Map<String, Object> row = mask(maskRule("f2", MaskType.EMAIL, null, null), "alice@example.com");

        assertEquals("a***@example.com", row.get("email"));
    }

    @Test
    void masksName() {
        Map<String, Object> row = mask(maskRule("f3", MaskType.NAME, null, null), "张三");

        assertEquals("张*", row.get("name"));
    }

    @Test
    void masksIdCard() {
        Map<String, Object> row = mask(maskRule("f4", MaskType.ID_CARD, null, null), "110101199001011234");

        assertEquals("110***********1234", row.get("id_card"));
    }

    @Test
    void appliesCustomPattern() {
        Map<String, Object> row = mask(maskRule("f5", MaskType.CUSTOM, "\\d", "*"), "订单 2026");

        assertEquals("订单 ****", row.get("remark"));
    }

    @Test
    void keepsNullValue() {
        Map<String, Object> row = mask(maskRule("f1", MaskType.PHONE, null, null), null);

        assertNull(row.get("phone"));
    }

    @Test
    void ignoresMaskRuleOfHiddenField() {
        FieldMask mask = new FieldMask(
                List.of("f1"), List.of(), List.of(),
                List.of(maskRule("f1", MaskType.PHONE, null, null)));

        List<Map<String, Object>> result = masker.apply(table, mask, List.of(row("13800138000")));

        assertEquals("13800138000", result.get(0).get("phone"));
    }

    @Test
    void doesNotMutateSourceRows() {
        Map<String, Object> source = row("13800138000");
        List<Map<String, Object>> result = masker.apply(
                table, new FieldMask(List.of(), List.of(), List.of(),
                        List.of(maskRule("f1", MaskType.PHONE, null, null))),
                List.of(source));

        assertEquals("13800138000", source.get("phone"));
        assertEquals("138****8000", result.get(0).get("phone"));
    }

    @Test
    void returnsSameListWhenNoMaskRules() {
        List<Map<String, Object>> rows = List.of(row("13800138000"));

        assertSame(rows, masker.apply(table, FieldMask.empty(), rows));
    }

    private Map<String, Object> mask(MaskRule rule, String value) {
        FieldMask mask = new FieldMask(List.of(), List.of(), List.of(), List.of(rule));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(table.columnByFieldId(rule.fieldId()).fieldCode(), value);
        return masker.apply(table, mask, List.of(row)).get(0);
    }

    private static Map<String, Object> row(String phone) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phone", phone);
        return row;
    }

    private static MaskRule maskRule(String fieldId, MaskType type, String pattern, String replacement) {
        return new MaskRule(fieldId, type, pattern, replacement);
    }

    private static SchemaField field(String id, String code) {
        return new SchemaField(
                id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                new FieldConstraints(false, false, false, false, true, true,
                        false, false, true, null, null, false, null),
                FieldUiHint.empty(),
                MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
