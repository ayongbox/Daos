package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.core.spi.SnapshotStore;

import java.util.Objects;

/**
 * 从登录会话解析权限上下文。
 *
 * <p>优先使用应用当前生效快照；应用尚未发布时回退到草稿权限，使首次建模可用。
 * 草稿只在「没有生效版本」时参与鉴权，已发布应用的权限变更必须重新发布才生效。</p>
 *
 * <p>账号未关联组织成员或应用既无快照也无草稿时显式失败，不做降级放行。</p>
 */
public final class SessionPermissionService {

    private final SnapshotStore snapshotStore;
    private final MetadataStore metadataStore;
    private final PermissionContextFactory contextFactory;

    public SessionPermissionService(
            SnapshotStore snapshotStore,
            MetadataStore metadataStore,
            PermissionContextFactory contextFactory) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
    }

    public PermissionContext resolve(Session session, String appId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(appId, "appId");
        if (session.memberId() == null) {
            throw new AccessDeniedException("账号未关联组织成员: " + session.subjectId());
        }

        SchemaSnapshot snapshot = snapshotStore.findActive(appId).orElse(null);
        if (snapshot != null) {
            return create(session, appId, snapshot.app());
        }

        SchemaApp draft = metadataStore.findDraft(appId)
                .orElseThrow(() -> new IllegalStateException("应用 " + appId + " 没有草稿或生效版本"));
        return create(session, appId, draft);
    }

    private PermissionContext create(Session session, String appId, SchemaApp app) {
        return contextFactory.create(
                session.tenantId(), appId, session.memberId(), app.permissions());
    }
}
