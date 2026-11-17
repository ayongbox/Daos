package io.github.ayongbox.daos.core.ir;

import io.github.ayongbox.daos.core.metadata.form.SchemaForm;

import java.util.Objects;

/**
 * 表单的编译视图。保留原始元数据，并补充归一化阶段解析出的所属表编码。
 *
 * <p>表单项对字段的引用已在归一化阶段校验，渲染编译器只读本 IR，不再回查元数据仓库。</p>
 */
public record IrForm(
        SchemaForm source,
        String tableCode) {

    public IrForm {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(tableCode, "tableCode");
    }

    public String formId() {
        return source.id();
    }

    public String formCode() {
        return source.code();
    }

    public String tableId() {
        return source.tableId();
    }
}
