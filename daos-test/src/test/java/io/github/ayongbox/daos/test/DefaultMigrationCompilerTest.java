package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.ir.MigrationPlan;
import io.github.ayongbox.daos.core.ir.MigrationPlan.MigrationStep;
import io.github.ayongbox.daos.core.ir.MigrationPlan.Operation;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.SortDirection;
import io.github.ayongbox.daos.core.metadata.common.StorageMode;
import io.github.ayongbox.daos.core.metadata.common.TableKind;
import io.github.ayongbox.daos.core.metadata.common.TableOwnership;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.FieldConstraints;
import io.github.ayongbox.daos.core.metadata.field.FieldUiHint;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.metadata.table.RelationDef;
import io.github.ayongbox.daos.core.metadata.table.SchemaTable;
import io.github.ayongbox.daos.core.metadata.table.TableOptions;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.ddl.DefaultMigrationCompiler;
import io.github.ayongbox.daos.runtime.engine.dialect.PostgresDialect;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMigrationCompilerTest {

    private static final String APP_ID = "app-1";
    private static final String APP_CODE = "demo";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private final SchemaNormalizer normalizer = new SchemaNormalizer();
    private final DefaultMigrationCompiler compiler = new DefaultMigrationCompiler();
    private final PostgresDialect dialect = new PostgresDialect();

    @Test
    void firstReleaseCreatesTableAndIndexes() {
        NormalizedSchema target = normalize(1, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, constraints(false, true)))));

        MigrationPlan plan = compiler.plan(null, target, dialect);

        assertEquals(2, plan.steps().size());
        assertEquals(Operation.CREATE_TABLE, plan.steps().get(0).operation());
        assertEquals(Operation.CREATE_INDEX, plan.steps().get(1).operation());
        assertTrue(plan.steps().get(1).forwardSql().contains("uk_title"));
        assertTrue(plan.rollbackStatements().get(1).sql().contains("DROP TABLE"));
    }

    @Test
    void addColumnProducesReversibleStep() {
        NormalizedSchema from = normalize(1, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults()))));
        NormalizedSchema to = normalize(2, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults()),
                field("f-2", "summary", FieldLogicalType.TEXT_LONG, FieldConstraints.defaults()))));

        MigrationPlan plan = compiler.plan(from, to, dialect);

        assertEquals(1, plan.steps().size());
        MigrationStep step = plan.steps().get(0);
        assertEquals(Operation.ADD_COLUMN, step.operation());
        assertEquals("summary", step.objectName());
        assertTrue(step.forwardSql().contains("ADD COLUMN"));
        assertTrue(step.rollbackSql().contains("DROP COLUMN"));
    }

    @Test
    void renameColumnDetectedByFieldId() {
        NormalizedSchema from = normalize(1, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults()))));
        NormalizedSchema to = normalize(2, app(TableOptions.defaults(), List.of(
                field("f-1", "name", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults()))));

        MigrationPlan plan = compiler.plan(from, to, dialect);

        assertEquals(1, plan.steps().size());
        MigrationStep step = plan.steps().get(0);
        assertEquals(Operation.RENAME_COLUMN, step.operation());
        assertTrue(step.forwardSql().contains("RENAME COLUMN \"title\" TO \"name\""));
        assertTrue(step.rollbackSql().contains("RENAME COLUMN \"name\" TO \"title\""));
    }

    @Test
    void alterColumnWhenTypeChanges() {
        NormalizedSchema from = normalize(1, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults()))));
        NormalizedSchema to = normalize(2, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_LONG, FieldConstraints.defaults()))));

        MigrationPlan plan = compiler.plan(from, to, dialect);

        assertEquals(1, plan.steps().size());
        assertEquals(Operation.ALTER_COLUMN, plan.steps().get(0).operation());
        assertTrue(plan.steps().get(0).forwardSql().contains("TYPE text"));
    }

    @Test
    void indexChangesAreAppliedAroundColumns() {
        NormalizedSchema from = normalize(1, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults()))));
        NormalizedSchema to = normalize(2, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, constraints(false, true)))));

        MigrationPlan plan = compiler.plan(from, to, dialect);

        assertEquals(1, plan.steps().size());
        assertEquals(Operation.CREATE_INDEX, plan.steps().get(0).operation());
    }

    @Test
    void rejectsRemovingTenantColumn() {
        NormalizedSchema from = normalize(1, app(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults()))));
        TableOptions noTenant = new TableOptions(
                true, true, false, true, true, true, true, true,
                null, 20, List.of(), null, null, false, true, true, false);
        NormalizedSchema to = normalize(2, app(noTenant, List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults()))));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> compiler.plan(from, to, dialect));

        assertTrue(error.getMessage().contains("tenant_id"));
    }

    @Test
    void rejectsDroppingTable() {
        SchemaTable users = users(TableOptions.defaults(), List.of(
                field("f-1", "title", FieldLogicalType.TEXT_SHORT, FieldConstraints.defaults())));
        SchemaTable legacy = new SchemaTable(
                "t2", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "legacy", "历史", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(),
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        NormalizedSchema from = normalize(1, appOf(List.of(users, legacy)));
        NormalizedSchema to = normalize(2, appOf(List.of(users)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> compiler.plan(from, to, dialect));

        assertTrue(error.getMessage().contains("暂不支持删除表"));
    }

    private NormalizedSchema normalize(long version, SchemaApp app) {
        return normalizer.normalize(APP_CODE, "s-" + version, version, app.tables(), app.forms());
    }

    private static SchemaApp app(TableOptions options, List<SchemaField> fields) {
        return appOf(List.of(users(options, fields)));
    }

    private static SchemaApp appOf(List<SchemaTable> tables) {
        return new SchemaApp(
                APP_ID, APP_CODE, "演示应用", null, null, MetadataStatus.DRAFT,
                tables, List.of(), List.of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaTable users(TableOptions options, List<SchemaField> fields) {
        return new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "users", "用户", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.PUBLISHED, false, null, List.of(), null,
                SortDirection.ASC, options, fields,
                List.of(), List.<RelationDef>of(), Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaField field(
            String id, String code, FieldLogicalType type, FieldConstraints constraints) {
        FieldConfig config = switch (type) {
            case TEXT_SHORT -> new FieldConfig.TextShortConfig(255, 0, null, null, false);
            case TEXT_LONG -> new FieldConfig.TextLongConfig(0, 0, 3, false);
            default -> throw new IllegalArgumentException("测试未配置该逻辑类型: " + type);
        };
        return new SchemaField(
                id, APP_ID, "t1", code, code, null, type, config, constraints,
                FieldUiHint.empty(), MetadataStatus.PUBLISHED, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static FieldConstraints constraints(boolean required, boolean unique) {
        return new FieldConstraints(required, unique, false, false, false, true,
                false, false, true, null, null, false, null);
    }
}
