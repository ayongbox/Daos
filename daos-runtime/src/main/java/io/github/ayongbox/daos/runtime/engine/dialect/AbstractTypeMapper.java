package io.github.ayongbox.daos.runtime.engine.dialect;

import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.field.FieldConfig;
import io.github.ayongbox.daos.core.metadata.field.SchemaField;
import io.github.ayongbox.daos.core.pipeline.dialect.TypeMapper;

/**
 * 逻辑类型矩阵的公共实现。子类只需声明 4 处真正的方言差异：json、时间戳、布尔与小整数。
 */
abstract class AbstractTypeMapper implements TypeMapper {

    @Override
    public String sqlType(SchemaField field) {
        FieldLogicalType logicalType = field.logicalType();
        FieldConfig config = field.config();
        return switch (logicalType) {
            case TEXT_SHORT -> varchar(textShortLength(config));
            case TEXT_LONG, RICH_TEXT -> textType();
            case EMAIL -> varchar(255);
            case PHONE -> varchar(32);
            case URL -> varchar(512);
            case NUMBER -> bigintType();
            case DECIMAL -> decimalType(decimalPrecision(config), decimalScale(config));
            case DATE -> dateType();
            case DATETIME -> timestampType(3);
            case SELECT -> varchar(64);
            case MULTI_SELECT, MEMBER_MULTI, DEPARTMENT_MULTI, ATTACHMENT -> jsonType();
            case BOOLEAN -> booleanType();
            case MEMBER, DEPARTMENT -> varchar(36);
            case RELATION -> relationMultiple(config) ? jsonType() : varchar(36);
            case ROLLUP, FORMULA -> throw new IllegalArgumentException(
                    "计算字段不落物理列: " + field.code());
            case AUTO_NUMBER -> varchar(64);
            case RATING, PROGRESS -> smallIntType();
            case LOCATION -> jsonType();
            case BARCODE -> varchar(512);
        };
    }

    @Override
    public String autoColumnType(AutoColumn column) {
        return switch (column) {
            case ID -> varchar(26);
            case TENANT_ID, CREATED_BY, UPDATED_BY -> varchar(36);
            case CREATED_AT, UPDATED_AT -> timestampType(3);
            case VERSION -> intType();
            case DELETED -> booleanType();
            case EXT -> jsonType();
        };
    }

    protected abstract String jsonType();

    protected abstract String timestampType(int precision);

    protected abstract String booleanType();

    protected abstract String smallIntType();

    protected String varchar(int length) {
        return "varchar(" + length + ")";
    }

    protected String decimalType(int precision, int scale) {
        return "decimal(" + precision + "," + scale + ")";
    }

    protected String textType() {
        return "text";
    }

    protected String dateType() {
        return "date";
    }

    protected String bigintType() {
        return "bigint";
    }

    protected String intType() {
        return "int";
    }

    private static int textShortLength(FieldConfig config) {
        if (config instanceof FieldConfig.TextShortConfig text && text.maxLength() > 0) {
            return Math.min(text.maxLength(), 4096);
        }
        return 255;
    }

    private static int decimalPrecision(FieldConfig config) {
        if (config instanceof FieldConfig.DecimalConfig decimal && decimal.precision() > 0) {
            return Math.min(decimal.precision(), 65);
        }
        return 20;
    }

    private static int decimalScale(FieldConfig config) {
        if (config instanceof FieldConfig.DecimalConfig decimal && decimal.scale() >= 0) {
            return Math.min(decimal.scale(), decimalPrecision(config));
        }
        return 6;
    }

    private static boolean relationMultiple(FieldConfig config) {
        return config instanceof FieldConfig.RelationConfig relation && relation.multiple();
    }
}
