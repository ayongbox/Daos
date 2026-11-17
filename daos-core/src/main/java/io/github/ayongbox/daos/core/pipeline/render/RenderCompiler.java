package io.github.ayongbox.daos.core.pipeline.render;

import io.github.ayongbox.daos.core.ir.NormalizedSchema;
import io.github.ayongbox.daos.core.ir.RenderTree;
import io.github.ayongbox.daos.core.metadata.common.FormScene;
import io.github.ayongbox.daos.core.metadata.permission.FieldMask;

/**
 * 渲染树编译器。权限掩码是入参，同一表单在不同权限下产出不同树。
 * 被掩码隐藏的字段不会出现在产物中，运行时不再做二次裁剪。
 */
public interface RenderCompiler {

    RenderTree compile(
            NormalizedSchema schema,
            String formId,
            FormScene scene,
            FieldMask fieldMask);
}
