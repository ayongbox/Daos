package io.github.ayongbox.daos.runtime.app;

import io.github.ayongbox.daos.core.common.Ids;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.metadata.common.ActionType;
import io.github.ayongbox.daos.core.metadata.common.EffectType;
import io.github.ayongbox.daos.core.metadata.common.MetadataStatus;
import io.github.ayongbox.daos.core.metadata.common.ResourceType;
import io.github.ayongbox.daos.core.metadata.common.SubjectType;
import io.github.ayongbox.daos.core.metadata.identity.RoleBinding;
import io.github.ayongbox.daos.core.metadata.identity.RoleDefinition;
import io.github.ayongbox.daos.core.metadata.identity.RoleScope;
import io.github.ayongbox.daos.core.metadata.permission.PermissionSubject;
import io.github.ayongbox.daos.core.metadata.permission.ResourceRef;
import io.github.ayongbox.daos.core.metadata.permission.SchemaPermission;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.IdentityStore;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.engine.SchemaContentHasher;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 应用草稿管理。创建应用时同时建立「应用所有者」角色与设计/发布权限，并绑定创建人，
 * 使第一个应用在没有生效快照时也能进入建模。
 */
public final class AppService {

    private static final String OWNER_ROLE_CODE = "owner";
    private static final List<ActionType> OWNER_ACTIONS = List.of(
            ActionType.VIEW, ActionType.DESIGN, ActionType.PUBLISH,
            ActionType.EXPORT, ActionType.DELETE, ActionType.MANAGE_MEMBER);
    private static final List<ActionType> OWNER_TABLE_ACTIONS = List.of(
            ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE);

    private final MetadataStore metadataStore;
    private final IdentityStore identityStore;
    private final SnapshotStore snapshotStore;
    private final Clock clock;
    private final SchemaContentHasher contentHasher = new SchemaContentHasher();

    public AppService(
            MetadataStore metadataStore,
            IdentityStore identityStore,
            SnapshotStore snapshotStore,
            Clock clock) {
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore");
        this.identityStore = Objects.requireNonNull(identityStore, "identityStore");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public List<SchemaApp> listDrafts() {
        return metadataStore.findAllDrafts();
    }

    /**
     * 应用的发布状态。生效版本取自快照指针，草稿改动通过内容哈希比对得出，
     * 与草稿记录自身的 status 无关。
     */
    public ReleaseInfo releaseInfo(SchemaApp app) {
        Objects.requireNonNull(app, "app");
        Optional<SchemaSnapshot> active = snapshotStore.findActive(app.id());
        if (active.isEmpty()) {
            return new ReleaseInfo(0L, false);
        }
        SchemaSnapshot snapshot = active.get();
        return new ReleaseInfo(
                snapshot.releaseVersion(),
                !contentHasher.hash(app).equals(snapshot.contentHash()));
    }

    public record ReleaseInfo(long activeReleaseVersion, boolean changedSinceRelease) {
    }

    /**
     * 软删除应用：只标记删除，保留迁移台账与物理表。
     * 物理表的回收不属于应用管理，避免误删仍在被引用的数据。
     */
    public void delete(String appId, String operator) {
        requireText(appId, "应用");
        SchemaApp app = metadataStore.findDraft(appId)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + appId));
        SchemaApp deleted = new SchemaApp(
                app.id(), app.code(), app.displayName(), app.description(), app.icon(),
                app.status(), app.tables(), app.forms(), app.permissions(), app.ext(),
                app.createdBy(), app.createdAt(), operator,
                clock.instant().toString(), true, app.schemaVersion());
        metadataStore.saveDraft(deleted);
    }

    public SchemaApp create(
            String tenantId,
            String memberId,
            String operator,
            String code,
            String displayName,
            String description,
            String icon) {
        requireText(tenantId, "租户");
        requireText(memberId, "成员");
        requireText(displayName, "应用名称");

        // 普通用户只填名称，编码由服务端生成，作为内部技术标识。
        String resolvedCode = code == null || code.isBlank()
                ? "app_" + Ids.ulid().toLowerCase(Locale.ROOT)
                : code.trim();

        boolean duplicated = metadataStore.findAllDrafts().stream()
                .anyMatch(app -> app.code().equals(resolvedCode) && !app.deleted());
        if (duplicated) {
            throw new IllegalArgumentException("应用编码已存在: " + resolvedCode);
        }

        String now = Instant.now(clock).toString();
        String appId = Ids.ulid();

        RoleDefinition ownerRole = new RoleDefinition(
                Ids.ulid(), tenantId, appId, OWNER_ROLE_CODE, "应用所有者", RoleScope.APPLICATION,
                "创建应用时自动生成", true, Map.of(),
                operator, now, operator, now, false, 1);
        identityStore.saveRole(ownerRole);
        identityStore.saveBinding(new RoleBinding(
                Ids.ulid(), tenantId, ownerRole.id(), memberId, Map.of(),
                operator, now, operator, now, false, 1));

        SchemaPermission ownerPermission = new SchemaPermission(
                Ids.ulid(), appId, "owner_all", "应用所有者权限", null,
                new PermissionSubject(SubjectType.ROLE, ownerRole.id(), false),
                new ResourceRef(ResourceType.APP, resolvedCode, appId, List.of()),
                OWNER_ACTIONS, EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
                operator, now, operator, now, false, 1);

        // 数据表规则不限定 objectCode：权限裁决只比较资源类型与动作，一条规则覆盖应用内所有表。
        SchemaPermission ownerTablePermission = new SchemaPermission(
                Ids.ulid(), appId, "owner_tables", "应用所有者的数据表权限", null,
                new PermissionSubject(SubjectType.ROLE, ownerRole.id(), false),
                new ResourceRef(ResourceType.TABLE, resolvedCode, null, List.of()),
                OWNER_TABLE_ACTIONS, EffectType.ALLOW, null, null, null,
                100, true, null, null, false, Map.of(),
                operator, now, operator, now, false, 1);

        SchemaApp app = new SchemaApp(
                appId, resolvedCode, displayName, description,
                icon == null || icon.isBlank() ? null : icon.trim(), MetadataStatus.DRAFT,
                List.of(), List.of(), List.of(ownerPermission, ownerTablePermission), Map.of(),
                operator, now, operator, now, false, 1);
        metadataStore.saveDraft(app);
        return app;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
