package io.github.ayongbox.daos.runtime.engine.dialect;

import io.github.ayongbox.daos.core.ir.IrColumn;
import io.github.ayongbox.daos.core.ir.IrTable;
import io.github.ayongbox.daos.core.ir.NormalizedSchema;
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
            case ROLLUP -> rollupType(config, null, null);
            case FORMULA -> formulaType(config);
            case AUTO_NUMBER -> varchar(64);
            case RATING, PROGRESS -> smallIntType();
            case LOCATION -> jsonType();
            case BARCODE -> varchar(512);
        };
    }

    @Override
    public String sqlType(SchemaField field, IrTable table) {
        FieldConfig config = field.config();
        if (field.logicalType() == FieldLogicalType.ROLLUP) {
            return rollupType(config, null, table);
        }
        if (field.logicalType() == FieldLogicalType.FORMULA) {
            return formulaType(config);
        }
        return sqlType(field);
    }

    @Override
    public String sqlType(SchemaField field, IrTable table, NormalizedSchema schema) {
        if (field.logicalType() == FieldLogicalType.ROLLUP) {
            return rollupType(field.config(), schema, table);
        }
        return sqlType(field, table);
    }

    private String formulaType(FieldConfig config) {
        if (!(config instanceof FieldConfig.FormulaConfig formula)) {
            return varchar(1024);
        }
        String resultType = formula.resultType() == null ? "text" : formula.resultType().toLowerCase();
        int scale = formula.precision() > 0 ? formula.precision() : 6;
        return switch (resultType) {
            case "number", "integer", "int", "long" -> bigintType();
            case "decimal", "double", "float" -> decimalType(20, Math.min(scale, 20));
            case "boolean", "bool" -> booleanType();
            case "date" -> dateType();
            default -> varchar(1024);
        };
    }

    private String rollupType(FieldConfig config, NormalizedSchema schema, IrTable table) {
        if (!(config instanceof FieldConfig.RollupConfig rollup)) {
            return decimalType(20, 6);
        }
        String aggregate = rollup.aggregate() == null ? "count" : rollup.aggregate().toLowerCase();
        IrColumn source = sourceColumn(schema, table, rollup);
        return switch (aggregate) {
            case "count" -> bigintType();
            case "sum", "avg" -> decimalType(20, Math.max(0, Math.min(rollup.precision(), 20)));
            case "max", "min" -> sourceTypeOrDefault(source);
            default -> decimalType(20, 6);
        };
    }

    private String sourceTypeOrDefault(IrColumn source) {
        if (source == null) {
            return decimalType(20, 6);
        }
        FieldConfig config = source.source().config();
        return switch (source.source().logicalType()) {
            case NUMBER -> bigintType();
            case RATING, PROGRESS -> smallIntType();
            case DECIMAL -> decimalType(decimalPrecision(config), decimalScale(config));
            case DATE -> dateType();
            case DATETIME -> timestampType(3);
            case BOOLEAN -> booleanType();
            default -> varchar(1024);
        };
    }

    private IrColumn sourceColumn(
            NormalizedSchema schema, IrTable table, FieldConfig.RollupConfig rollup) {
        if (rollup.relationFieldId() == null || rollup.sourceFieldId() == null) {
            return null;
        }
        if (schema != null && table != null) {
            for (IrTable child : schema.tablesById().values()) {
                IrColumn relation = child.columnByFieldId(rollup.relationFieldId());
                if (relation != null
                        && relation.source().config() instanceof FieldConfig.RelationConfig config
                        && table.tableId().equals(config.targetTableId())) {
                    return child.columnByFieldId(rollup.sourceFieldId());
                }
            }
        }
        return table == null ? null : table.columnByFieldId(rollup.sourceFieldId());
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
