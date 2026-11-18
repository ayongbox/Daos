package io.github.ayongbox.daos.runtime.form;

import io.github.ayongbox.daos.core.ir.IrForm;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.RenderTree;
import io.github.ayongbox.daos.core.metadata.common.FormScene;
import io.github.ayongbox.daos.core.metadata.form.SchemaForm;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;
import io.github.ayongbox.daos.core.pipeline.render.RenderCompiler;
import io.github.ayongbox.daos.core.snapshot.SchemaSnapshot;
import io.github.ayongbox.daos.core.spi.SnapshotStore;
import io.github.ayongbox.daos.runtime.engine.SchemaNormalizer;
import io.github.ayongbox.daos.runtime.engine.render.DefaultRenderCompiler;

import java.util.List;
import java.util.Objects;

/**
 * 表单渲染服务。运行时只读生效快照，草稿改动必须发布后才会反映到表单与渲染树。
 *
 * <p>渲染树在编译期完成字段裁剪与必填/只读合并，调用方不得再放宽权限。</p>
 */
public final class FormRenderService {

    private final SnapshotStore snapshotStore;
    private final SchemaNormalizer normalizer = new SchemaNormalizer();
    private final RenderCompiler renderCompiler = new DefaultRenderCompiler();

    public FormRenderService(SnapshotStore snapshotStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
    }

    public List<FormSummary> listForms(String appId) {
        SchemaSnapshot snapshot = requireActive(appId);
        return snapshot.app().forms().stream()
                .filter(form -> !form.deleted())
                .map(form -> new FormSummary(
                        form.id(),
                        form.code(),
                        form.displayName(),
                        form.tableId(),
                        tableCodeOf(snapshot, form.tableId()),
                        form.formType().name()))
                .toList();
    }

    public FormRenderResult render(String appId, String formCode, FormScene scene) {
        Objects.requireNonNull(formCode, "formCode");
        SchemaSnapshot snapshot = requireActive(appId);
        NormalizedSchema schema = normalizer.normalize(
                snapshot.appCode(),
                snapshot.snapshotId(),
                snapshot.releaseVersion(),
                snapshot.app().tables(),
                snapshot.app().forms());

        IrForm form = schema.requireFormByCode(formCode);
        FormScene resolvedScene = scene == null ? FormScene.CREATE : scene;
        RenderTree tree = renderCompiler.compile(
                schema, form.formId(), resolvedScene, FieldMask.empty());

        return new FormRenderResult(
                form.formId(),
                form.formCode(),
                form.source().displayName(),
                form.tableId(),
                form.tableCode(),
                resolvedScene.name(),
                tree);
    }

    private SchemaSnapshot requireActive(String appId) {
        Objects.requireNonNull(appId, "appId");
        return snapshotStore.findActive(appId)
                .orElseThrow(() -> new IllegalStateException("应用还没有生效版本: " + appId));
    }

    private static String tableCodeOf(SchemaSnapshot snapshot, String tableId) {
        return snapshot.app().tables().stream()
                .filter(table -> !table.deleted())
                .filter(table -> table.id().equals(tableId))
                .map(table -> table.code())
                .findFirst()
                .orElse(null);
    }

    /**
     * 表单列表项。只暴露运行时需要的展示信息，不返回字段与权限细节。
     */
    public record FormSummary(
            String formId,
            String formCode,
            String displayName,
            String tableId,
            String tableCode,
            String formType) {
    }

    /**
     * 渲染结果。附带表单所属表编码，运行时据此提交记录。
     */
    public record FormRenderResult(
            String formId,
            String formCode,
            String displayName,
            String tableId,
            String tableCode,
            String scene,
            RenderTree tree) {
    }
}
