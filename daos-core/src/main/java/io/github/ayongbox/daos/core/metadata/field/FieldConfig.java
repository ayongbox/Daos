package io.github.ayongbox.daos.core.metadata.field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.ayongbox.daos.core.metadata.common.DictSource;
import io.github.ayongbox.daos.core.metadata.common.FieldLogicalType;
import io.github.ayongbox.daos.core.metadata.common.OnDeleteAction;

import java.math.BigDecimal;
import java.util.List;

/**
 * 逻辑类型的专属配置。每个逻辑类型一个不可变配置，字段必须能覆盖对应类型的全部可配置能力。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "configType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FieldConfig.TextShortConfig.class, name = "text_short"),
        @JsonSubTypes.Type(value = FieldConfig.TextLongConfig.class, name = "text_long"),
        @JsonSubTypes.Type(value = FieldConfig.RichTextConfig.class, name = "rich_text"),
        @JsonSubTypes.Type(value = FieldConfig.EmailConfig.class, name = "email"),
        @JsonSubTypes.Type(value = FieldConfig.PhoneConfig.class, name = "phone"),
        @JsonSubTypes.Type(value = FieldConfig.UrlConfig.class, name = "url"),
        @JsonSubTypes.Type(value = FieldConfig.NumberConfig.class, name = "number"),
        @JsonSubTypes.Type(value = FieldConfig.DecimalConfig.class, name = "decimal"),
        @JsonSubTypes.Type(value = FieldConfig.DateConfig.class, name = "date"),
        @JsonSubTypes.Type(value = FieldConfig.DateTimeConfig.class, name = "datetime"),
        @JsonSubTypes.Type(value = FieldConfig.SelectConfig.class, name = "select"),
        @JsonSubTypes.Type(value = FieldConfig.MultiSelectConfig.class, name = "multi_select"),
        @JsonSubTypes.Type(value = FieldConfig.BooleanConfig.class, name = "boolean"),
        @JsonSubTypes.Type(value = FieldConfig.MemberConfig.class, name = "member"),
        @JsonSubTypes.Type(value = FieldConfig.MemberMultiConfig.class, name = "member_multi"),
        @JsonSubTypes.Type(value = FieldConfig.DepartmentConfig.class, name = "department"),
        @JsonSubTypes.Type(value = FieldConfig.DepartmentMultiConfig.class, name = "department_multi"),
        @JsonSubTypes.Type(value = FieldConfig.AttachmentConfig.class, name = "attachment"),
        @JsonSubTypes.Type(value = FieldConfig.RelationConfig.class, name = "relation"),
        @JsonSubTypes.Type(value = FieldConfig.RollupConfig.class, name = "rollup"),
        @JsonSubTypes.Type(value = FieldConfig.FormulaConfig.class, name = "formula"),
        @JsonSubTypes.Type(value = FieldConfig.AutoNumberConfig.class, name = "auto_number"),
        @JsonSubTypes.Type(value = FieldConfig.RatingConfig.class, name = "rating"),
        @JsonSubTypes.Type(value = FieldConfig.ProgressConfig.class, name = "progress"),
        @JsonSubTypes.Type(value = FieldConfig.LocationConfig.class, name = "location"),
        @JsonSubTypes.Type(value = FieldConfig.BarcodeConfig.class, name = "barcode")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface FieldConfig {

    FieldLogicalType logicalType();

    record TextShortConfig(
            int maxLength,
            int minLength,
            String pattern,
            String patternMessage,
            boolean showCount) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.TEXT_SHORT;
        }
    }

    record TextLongConfig(
            int maxLength,
            int minLength,
            int rows,
            boolean showCount) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.TEXT_LONG;
        }
    }

    record RichTextConfig(
            int maxLength,
            List<String> toolbar,
            boolean allowImage,
            boolean allowVideo,
            boolean allowAttachment) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.RICH_TEXT;
        }
    }

    record EmailConfig(
            int maxLength,
            List<String> allowedDomains) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.EMAIL;
        }
    }

    record PhoneConfig(
            String region,
            boolean allowExtension) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.PHONE;
        }
    }

    record UrlConfig(
            int maxLength,
            List<String> allowedSchemes,
            boolean openInNewTab) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.URL;
        }
    }

    record NumberConfig(
            Long min,
            Long max,
            long step,
            int precision,
            String unit,
            boolean thousandsSeparator) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.NUMBER;
        }
    }

    record DecimalConfig(
            int precision,
            int scale,
            BigDecimal min,
            BigDecimal max,
            String currency,
            String format,
            String roundingMode,
            boolean thousandsSeparator) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.DECIMAL;
        }
    }

    record DateConfig(
            String format,
            String min,
            String max,
            String granularity,
            boolean defaultToday) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.DATE;
        }
    }

    record DateTimeConfig(
            String format,
            String min,
            String max,
            String timezone,
            String granularity,
            boolean defaultNow) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.DATETIME;
        }
    }

    record SelectConfig(
            DictSource source,
            String dictCode,
            List<OptionItem> options,
            String displayStyle,
            boolean allowOther,
            String colorMode) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.SELECT;
        }
    }

    record MultiSelectConfig(
            DictSource source,
            String dictCode,
            List<OptionItem> options,
            String displayStyle,
            int maxSelected,
            boolean allowCreate,
            String colorMode) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.MULTI_SELECT;
        }
    }

    record BooleanConfig(
            String trueLabel,
            String falseLabel,
            String displayStyle,
            Boolean defaultValue) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.BOOLEAN;
        }
    }

    record MemberConfig(
            boolean multi,
            List<String> scopeDeptIds,
            boolean includeSubDept,
            List<String> roleCodes,
            boolean allowExternal,
            String displayStyle) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.MEMBER;
        }
    }

    record MemberMultiConfig(
            List<String> scopeDeptIds,
            boolean includeSubDept,
            List<String> roleCodes,
            boolean allowExternal,
            String displayStyle,
            int maxSelected) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.MEMBER_MULTI;
        }
    }

    record DepartmentConfig(
            boolean multi,
            List<String> scopeDeptIds,
            boolean includeSubDept,
            String displayStyle) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.DEPARTMENT;
        }
    }

    record DepartmentMultiConfig(
            List<String> scopeDeptIds,
            boolean includeSubDept,
            String displayStyle,
            int maxSelected) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.DEPARTMENT_MULTI;
        }
    }

    record AttachmentConfig(
            int maxCount,
            int maxSizeMb,
            List<String> accept,
            String storage,
            boolean imageOnly,
            boolean compressImage,
            boolean privateAccess,
            boolean allowCamera) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.ATTACHMENT;
        }
    }

    record RelationConfig(
            String targetTableId,
            String displayFieldId,
            boolean multiple,
            int maxSelected,
            String filterExpression,
            OnDeleteAction onDelete,
            boolean quickCreate,
            String showAs,
            String bidirectionalFieldId) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.RELATION;
        }
    }

    record RollupConfig(
            String relationFieldId,
            String sourceFieldId,
            String aggregate,
            String filterExpression,
            int precision,
            boolean emptyAsZero) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.ROLLUP;
        }
    }

    record FormulaConfig(
            String expression,
            String resultType,
            int precision,
            String cacheMode) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.FORMULA;
        }
    }

    record AutoNumberConfig(
            String prefix,
            String datePattern,
            int serialLength,
            String resetCycle,
            long startValue,
            String suffix) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.AUTO_NUMBER;
        }
    }

    record RatingConfig(
            int max,
            boolean allowHalf,
            String icon) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.RATING;
        }
    }

    record ProgressConfig(
            int min,
            int max,
            String displayStyle,
            boolean showPercent) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.PROGRESS;
        }
    }

    record LocationConfig(
            String provider,
            boolean allowManual,
            boolean showMap,
            int defaultZoom) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.LOCATION;
        }
    }

    record BarcodeConfig(
            String format,
            boolean scannerEnabled,
            boolean unique) implements FieldConfig {

        @Override
        public FieldLogicalType logicalType() {
            return FieldLogicalType.BARCODE;
        }
    }
}
