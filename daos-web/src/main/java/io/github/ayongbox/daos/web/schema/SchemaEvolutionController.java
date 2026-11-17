package io.github.ayongbox.daos.web.schema;

import io.github.ayongbox.baseon.core.context.SecurityUtil;
import io.github.ayongbox.baseon.core.model.R;
import io.github.ayongbox.baseon.core.secure.PermissionChecker;
import io.github.ayongbox.daos.core.ir.MigrationPlan;
import io.github.ayongbox.daos.core.metadata.app.SchemaApp;
import io.github.ayongbox.daos.core.migration.MigrationRecord;
import io.github.ayongbox.daos.core.spi.MetadataStore;
import io.github.ayongbox.daos.runtime.engine.SchemaEvolutionService;
import io.github.ayongbox.daos.runtime.identity.AccessDeniedException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用结构管理：保存草稿、预览迁移、执行发布、回滚与查看历史。
 *
 * <p>所有操作要求 {@code table:design} 权限；迁移执行与回滚以当前登录用户记录操作人。</p>
 */
@RestController
@RequestMapping("/api/apps/{appId}/schema")
public class SchemaEvolutionController {

    private static final String DESIGN_PERMISSION = "table:design";

    private final MetadataStore metadataStore;
    private final SchemaEvolutionService evolutionService;
    private final PermissionChecker permissionChecker;

    public SchemaEvolutionController(
            MetadataStore metadataStore,
            SchemaEvolutionService evolutionService,
            PermissionChecker permissionChecker) {
        this.metadataStore = metadataStore;
        this.evolutionService = evolutionService;
        this.permissionChecker = permissionChecker;
    }

    @PutMapping("/draft")
    public R<Void> saveDraft(@PathVariable String appId, @RequestBody SchemaApp draft) {
        requireDesign(appId);
        if (!appId.equals(draft.id())) {
            throw new IllegalArgumentException("路径 appId 与草稿应用标识不一致");
        }
        metadataStore.saveDraft(draft);
        return R.ok();
    }

    @GetMapping("/migrations/preview")
    public R<MigrationPlan> preview(@PathVariable String appId) {
        requireDesign(appId);
        return R.ok(evolutionService.preview(appId));
    }

    @GetMapping("/migrations")
    public R<List<MigrationRecord>> history(@PathVariable String appId) {
        requireDesign(appId);
        return R.ok(evolutionService.history(appId));
    }

    @PostMapping("/migrations")
    public R<MigrationRecord> apply(@PathVariable String appId) {
        requireDesign(appId);
        return R.ok(evolutionService.apply(appId, operator()));
    }

    @PostMapping("/migrations/{migrationId}/rollback")
    public R<Void> rollback(@PathVariable String appId, @PathVariable String migrationId) {
        requireDesign(appId);
        evolutionService.rollback(appId, migrationId, operator());
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
}
