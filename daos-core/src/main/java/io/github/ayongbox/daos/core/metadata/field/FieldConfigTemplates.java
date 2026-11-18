package io.github.ayongbox.daos.core.metadata.field;

import io.github.ayongbox.daos.core.metadata.common.DictSource;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.OnDeleteAction;

import java.util.List;

/**
 * 逻辑类型的默认配置。设计器新增字段时以此生成合法配置，避免前端重复维护 26 套默认值。
 */
public final class FieldConfigTemplates {

    private FieldConfigTemplates() {
    }

    public static FieldConfig defaults(FieldLogicalType logicalType) {
        return switch (logicalType) {
            case TEXT_SHORT -> new FieldConfig.TextShortConfig(255, 0, null, null, false);
            case TEXT_LONG -> new FieldConfig.TextLongConfig(2000, 0, 4, false);
            case RICH_TEXT -> new FieldConfig.RichTextConfig(
                    20000, List.of("bold", "italic", "list", "link"), true, false, true);
            case EMAIL -> new FieldConfig.EmailConfig(255, List.of());
            case PHONE -> new FieldConfig.PhoneConfig("CN", false);
            case URL -> new FieldConfig.UrlConfig(2048, List.of("http", "https"), true);
            case NUMBER -> new FieldConfig.NumberConfig(null, null, 1, 0, null, false);
            case DECIMAL -> new FieldConfig.DecimalConfig(
                    18, 2, null, null, null, "#,##0.00", "HALF_UP", true);
            case DATE -> new FieldConfig.DateConfig("YYYY-MM-DD", null, null, "day", false);
            case DATETIME -> new FieldConfig.DateTimeConfig(
                    "YYYY-MM-DD HH:mm:ss", null, null, null, "second", false);
            case SELECT -> new FieldConfig.SelectConfig(
                    DictSource.INLINE, null, List.of(), "dropdown", false, "default");
            case MULTI_SELECT -> new FieldConfig.MultiSelectConfig(
                    DictSource.INLINE, null, List.of(), "tag", 0, false, "default");
            case BOOLEAN -> new FieldConfig.BooleanConfig("是", "否", "switch", null);
            case MEMBER -> new FieldConfig.MemberConfig(
                    false, List.of(), true, List.of(), false, "avatar");
            case MEMBER_MULTI -> new FieldConfig.MemberMultiConfig(
                    List.of(), true, List.of(), false, "avatar", 0);
            case DEPARTMENT -> new FieldConfig.DepartmentConfig(false, List.of(), true, "tree");
            case DEPARTMENT_MULTI -> new FieldConfig.DepartmentMultiConfig(
                    List.of(), true, "tree", 0);
            case ATTACHMENT -> new FieldConfig.AttachmentConfig(
                    10, 20, List.of(), "local", false, false, true, true);
            case RELATION -> new FieldConfig.RelationConfig(
                    null, null, false, 0, null, OnDeleteAction.RESTRICT, false, "select", null);
            case ROLLUP -> new FieldConfig.RollupConfig(null, null, "count", null, 0, false);
            case FORMULA -> new FieldConfig.FormulaConfig(null, "text", 0, "NONE");
            case AUTO_NUMBER -> new FieldConfig.AutoNumberConfig(
                    "", "", 4, "NONE", 1, null);
            case RATING -> new FieldConfig.RatingConfig(5, false, "star");
            case PROGRESS -> new FieldConfig.ProgressConfig(0, 100, "line", true);
            case LOCATION -> new FieldConfig.LocationConfig("amap", true, true, 12);
            case BARCODE -> new FieldConfig.BarcodeConfig("CODE_128", true, false);
        };
    }
}
