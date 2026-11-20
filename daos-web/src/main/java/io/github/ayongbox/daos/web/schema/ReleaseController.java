package io.github.ayongbox.daos.web.schema;

import io.github.ayongbox.baseon.core.context.SecurityUtil;
import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.engine.SchemaEvolutionService;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发布版本管理：版本列表查询与回滚到任意历史版本。
 *
 * <p>版本列表按发布版本倒序返回，{@code active} 标记当前生效版本；
 * 回滚与查询均要求 {@code app:design} 权限。</p>
 */
@RestController
@RequestMapping("/api/apps/{appId}/releases")
public class ReleaseController {

    private static final String DESIGN_PERMISSION = "app:design";

    private final SnapshotStore snapshotStore;
    private final SchemaEvolutionService evolutionService;
    private final PermissionChecker permissionChecker;

    public ReleaseController(
            SnapshotStore snapshotStore,
            SchemaEvolutionService evolutionService,
            PermissionChecker permissionChecker) {
        this.snapshotStore = snapshotStore;
        this.evolutionService = evolutionService;
        this.permissionChecker = permissionChecker;
    }

    @GetMapping
    public R<List<ReleaseView>> list(@PathVariable String appId) {
        requireDesign(appId);
        String activeId = snapshotStore.findActive(appId)
                .map(SchemaSnapshot::snapshotId)
                .orElse(null);
        return R.ok(snapshotStore.listByApp(appId).stream()
                .map(snapshot -> new ReleaseView(
                        snapshot.releaseVersion(), snapshot.snapshotId(), snapshot.contentHash(),
                        snapshot.createdBy(), snapshot.createdAt(), snapshot.remark(),
                        snapshot.snapshotId().equals(activeId)))
                .toList());
    }

    @PostMapping("/{releaseVersion}/rollback")
    public R<Void> rollback(
            @PathVariable String appId, @PathVariable long releaseVersion) {
        requireDesign(appId);
        evolutionService.rollbackTo(appId, releaseVersion, operator());
        return R.ok();
    }

    private void requireDesign(String appId) {
        if (!permissionChecker.hasPermission(appId, DESIGN_PERMISSION)) {
            throw new AccessDeniedException("无权限执行 " + DESIGN_PERMISSION);
        }
    }

    private static String operator() {
        String username = SecurityUtil.getUsername();
        return username == null || username.isBlank() ? "system" : username;
    }

    /**
     * 发布版本视图。{@code active} 为当前生效版本标记。
     */
    public record ReleaseView(
            long releaseVersion,
            String snapshotId,
            String contentHash,
            String createdBy,
            String createdAt,
            String remark,
            boolean active) {
    }
}
