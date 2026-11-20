package io.github.ayongbox.daos.test;

import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.OnDeleteAction;
import io.github.ayongbox.daos.core.metadata.common.RelationType;
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
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.runtime.engine.PublishService;
import io.github.ayongbox.daos.runtime.engine.SchemaContentHasher;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizeException;
import io.github.ayongbox.daos.runtime.store.InMemoryMetadataStore;
import io.github.ayongbox.daos.runtime.store.InMemorySnapshotStore;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishServiceTest {

    private static final String APP_ID = "app-1";
    private static final String TIMESTAMP = "2026-01-01T00:00:00Z";

    private final InMemoryMetadataStore metadataStore = new InMemoryMetadataStore();
    private final InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
    private final PublishService publishService = new PublishService(metadataStore, snapshotStore);

    @Test
    void publishesFirstSnapshotAndActivatesIt() {
        metadataStore.saveDraft(app("订单应用", "订单", List.of(text("f1", "title")), List.of()));

        SchemaSnapshot snapshot = publishService.publish(APP_ID, "tester");

        assertEquals(1L, snapshot.releaseVersion());
        assertTrue(Ids.isUlid(snapshot.snapshotId()));
        assertEquals(64, snapshot.contentHash().length());
        assertEquals("demo", snapshot.appCode());
        assertEquals("tester", snapshot.createdBy());
        assertEquals(snapshot.snapshotId(), snapshotStore.findActive(APP_ID).orElseThrow().snapshotId());
    }

    @Test
    void incrementsReleaseVersionMonotonically() {
        metadataStore.saveDraft(app("订单应用", "订单", List.of(text("f1", "title")), List.of()));
        SchemaSnapshot first = publishService.publish(APP_ID, "tester");

        metadataStore.saveDraft(app("订单应用", "订单V2", List.of(text("f1", "title")), List.of()));
        SchemaSnapshot second = publishService.publish(APP_ID, "tester");

        assertEquals(1L, first.releaseVersion());
        assertEquals(2L, second.releaseVersion());
        assertNotEquals(first.contentHash(), second.contentHash());
        assertEquals(second.snapshotId(), snapshotStore.findActive(APP_ID).orElseThrow().snapshotId());
        assertTrue(snapshotStore.findById(first.snapshotId()).isPresent());
    }

    @Test
    void draftChangesDoNotAffectActiveSnapshot() {
        metadataStore.saveDraft(app("订单应用", "订单", List.of(text("f1", "title")), List.of()));
        SchemaSnapshot first = publishService.publish(APP_ID, "tester");

        metadataStore.saveDraft(app("订单应用", "订单V2", List.of(text("f1", "title")), List.of()));

        SchemaSnapshot active = snapshotStore.findActive(APP_ID).orElseThrow();
        assertEquals(first.snapshotId(), active.snapshotId());
        assertEquals("订单", active.app().tables().get(0).displayName());
    }

    @Test
    void rejectsInvalidDraftWithoutTouchingActiveSnapshot() {
        metadataStore.saveDraft(app("订单应用", "订单", List.of(text("f1", "title")), List.of()));
        SchemaSnapshot first = publishService.publish(APP_ID, "tester");

        RelationDef broken = new RelationDef(
                "r1", "rel_missing", "缺失关联", RelationType.MANY_TO_ONE,
                "t1", "missing", null, null, null,
                OnDeleteAction.RESTRICT, OnDeleteAction.RESTRICT, false, null, null, true);
        metadataStore.saveDraft(app("订单应用", "订单", List.of(text("f1", "title")), List.of(broken)));

        assertThrows(SchemaNormalizeException.class, () -> publishService.publish(APP_ID, "tester"));

        assertEquals(1L, snapshotStore.latestReleaseVersion(APP_ID));
        assertEquals(first.snapshotId(), snapshotStore.findActive(APP_ID).orElseThrow().snapshotId());
    }

    @Test
    void identicalContentDoesNotCreateNewRelease() {
        SchemaApp draft = app("订单应用", "订单", List.of(text("f1", "title")), List.of());
        metadataStore.saveDraft(draft);
        SchemaSnapshot first = publishService.publish(APP_ID, "tester");

        metadataStore.saveDraft(draft);
        SchemaSnapshot second = publishService.publish(APP_ID, "tester");

        assertEquals(first.snapshotId(), second.snapshotId());
        assertEquals(1L, snapshotStore.latestReleaseVersion(APP_ID));
    }

    @Test
    void contentHashIgnoresCollectionAndMapOrder() {
        Map<String, Object> extLeft = new LinkedHashMap<>();
        extLeft.put("alpha", 1);
        extLeft.put("beta", "x");
        Map<String, Object> extRight = new LinkedHashMap<>();
        extRight.put("beta", "x");
        extRight.put("alpha", 1);

        SchemaApp left = app("订单应用",
                List.of(text("f1", "title"), text("f2", "amount")), Map.of("k", extLeft));
        SchemaApp right = app("订单应用",
                List.of(text("f2", "amount"), text("f1", "title")), Map.of("k", extRight));

        SchemaContentHasher hasher = new SchemaContentHasher();
        assertEquals(hasher.hash(left), hasher.hash(right));
        assertNotEquals(hasher.hash(left),
                hasher.hash(app("订单应用", List.of(text("f1", "title"), text("f2", "amount_v2")), Map.of())));
    }

    @Test
    void contentHashIgnoresAuditFields() {
        SchemaContentHasher hasher = new SchemaContentHasher();

        assertEquals(hasher.hash(appWithTimestamp(TIMESTAMP)),
                hasher.hash(appWithTimestamp("2027-09-09T09:09:09Z")));
    }

    @Test
    void snapshotStoreRejectsDuplicateReleaseVersion() {
        SchemaApp draft = app("订单应用", "订单", List.of(text("f1", "title")), List.of());
        snapshotStore.append(snapshot(1L, draft));

        assertThrows(IllegalStateException.class, () -> snapshotStore.append(snapshot(1L, draft)));
    }

    private static SchemaSnapshot snapshot(long releaseVersion, SchemaApp app) {
        return new SchemaSnapshot(Ids.ulid(), APP_ID, "demo", releaseVersion, 1,
                "a".repeat(64), "tester", TIMESTAMP, null, app);
    }

    private static SchemaApp appWithTimestamp(String timestamp) {
        SchemaField field = new SchemaField("f1", APP_ID, "t1", "title", "title", null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.DRAFT, false, false, 0, null, Map.of(),
                "system", timestamp, "system", timestamp, false, 1);
        SchemaTable table = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "orders", "订单", null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.DRAFT, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), List.of(field), List.of(), List.of(), Map.of(),
                "system", timestamp, "system", timestamp, false, 1);
        return new SchemaApp(
                APP_ID, "demo", "订单应用", null, null, MetadataStatus.DRAFT,
                List.of(table), List.of(), List.of(), Map.of(),
                "system", timestamp, "system", timestamp, false, 1);
    }

    private static SchemaApp app(
            String displayName, String tableDisplayName, List<SchemaField> fields, List<RelationDef> relations) {
        return app(displayName, tableDisplayName, fields, relations, Map.of());
    }

    private static SchemaApp app(
            String displayName,
            String tableDisplayName,
            List<SchemaField> fields,
            List<RelationDef> relations,
            Map<String, Object> ext) {
        SchemaTable table = new SchemaTable(
                "t1", APP_ID, TableOwnership.APPLICATION, false,
                StorageMode.PHYSICAL, List.of(), null,
                "orders", tableDisplayName, null, null, null, null, 0,
                TableKind.MAIN, MetadataStatus.DRAFT, false, null, List.of(), null,
                SortDirection.ASC, TableOptions.defaults(), fields, List.of(), relations, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);

        return new SchemaApp(
                APP_ID, "demo", displayName, null, null, MetadataStatus.DRAFT,
                List.of(table), List.of(), List.of(), ext,
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }

    private static SchemaApp app(
            String displayName, List<SchemaField> fields, Map<String, Object> ext) {
        return app(displayName, "订单", fields, List.of(), ext);
    }

    private static SchemaField text(String id, String code) {
        return new SchemaField(id, APP_ID, "t1", code, code, null,
                FieldLogicalType.TEXT_SHORT,
                new FieldConfig.TextShortConfig(255, 0, null, null, false),
                FieldConstraints.defaults(), FieldUiHint.empty(),
                MetadataStatus.DRAFT, false, false, 0, null, Map.of(),
                "system", TIMESTAMP, "system", TIMESTAMP, false, 1);
    }
}
