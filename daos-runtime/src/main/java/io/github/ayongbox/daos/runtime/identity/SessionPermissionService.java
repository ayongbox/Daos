package io.github.ayongbox.daos.runtime.identity;

import io.github.ayongbox.daos.core.auth.Session;
import io.github.ayongbox.daos.core.ir.PermissionContext;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;

import java.util.Objects;

/**
 * 从登录会话解析权限上下文。权限规则取自应用当前生效快照，草稿不参与鉴权。
 *
 * <p>账号未关联组织成员或应用没有生效版本时显式失败，不做降级放行。</p>
 */
public final class SessionPermissionService {

    private final SnapshotStore snapshotStore;
    private final PermissionContextFactory contextFactory;

    public SessionPermissionService(
            SnapshotStore snapshotStore, PermissionContextFactory contextFactory) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contextFactory = Objects.requireNonNull(contextFactory, "contextFactory");
    }

    public PermissionContext resolve(Session session, String appId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(appId, "appId");
        if (session.memberId() == null) {
            throw new AccessDeniedException("账号未关联组织成员: " + session.subjectId());
        }

        SchemaSnapshot snapshot = snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用 " + appId + " 没有生效版本"));
        return contextFactory.create(
                session.tenantId(), appId, session.memberId(), snapshot.app().permissions());
    }
}
