package io.github.ayongbox.daos.core.metadata.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 字段逻辑类型。物理类型映射只存在于编译器中，不进入元数据。
 */
public enum FieldLogicalType {

    TEXT_SHORT("text_short", "单行文本"),
    TEXT_LONG("text_long", "多行文本"),
    RICH_TEXT("rich_text", "富文本"),
    EMAIL("email", "邮箱"),
    PHONE("phone", "电话"),
    URL("url", "网址"),
    NUMBER("number", "数字"),
    DECIMAL("decimal", "金额 / 小数"),
    DATE("date", "日期"),
    DATETIME("datetime", "日期时间"),
    SELECT("select", "单选"),
    MULTI_SELECT("multi_select", "多选"),
    BOOLEAN("boolean", "布尔"),
    MEMBER("member", "成员"),
    MEMBER_MULTI("member_multi", "成员（多选）"),
    DEPARTMENT("department", "部门"),
    DEPARTMENT_MULTI("department_multi", "部门（多选）"),
    ATTACHMENT("attachment", "附件 / 图片"),
    RELATION("relation", "关联记录"),
    ROLLUP("rollup", "引用汇总"),
    FORMULA("formula", "公式"),
    AUTO_NUMBER("auto_number", "自动编号"),
    RATING("rating", "评分"),
    PROGRESS("progress", "进度"),
    LOCATION("location", "地理位置"),
    BARCODE("barcode", "条码 / 二维码");

    private final String code;
    private final String label;

    FieldLogicalType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean multiValued() {
        return this == MULTI_SELECT || this == MEMBER_MULTI || this == DEPARTMENT_MULTI;
    }

    /**
     * 是否计算字段。自动编号虽然是平台生成，但必须落列，不属于计算字段。
     */
    public boolean computed() {
        return this == ROLLUP || this == FORMULA;
    }

    @JsonCreator
    public static FieldLogicalType fromCode(String code) {
        for (FieldLogicalType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知字段逻辑类型: " + code);
    }
}
