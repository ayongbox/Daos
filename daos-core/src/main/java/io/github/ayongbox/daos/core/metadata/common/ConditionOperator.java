package io.github.ayongbox.daos.core.metadata.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 条件运算符，联动、行级权限、筛选共用同一套编码。
 */
public enum ConditionOperator {

    EQ("eq"),
    NE("ne"),
    CONTAINS("contains"),
    NOT_CONTAINS("not_contains"),
    STARTS_WITH("starts_with"),
    ENDS_WITH("ends_with"),
    EMPTY("empty"),
    NOT_EMPTY("not_empty"),
    GT("gt"),
    GTE("gte"),
    LT("lt"),
    LTE("lte"),
    BETWEEN("between"),
    IN("in"),
    NOT_IN("not_in"),
    ANY_OF("any_of"),
    ALL_OF("all_of"),
    NONE_OF("none_of"),
    IS_SELF("is_self"),
    IN_DEPT("in_dept"),
    IN_DEPT_TREE("in_dept_tree"),
    HAS_FILE("has_file"),
    NO_FILE("no_file"),
    CURRENT_USER("current_user"),
    CURRENT_DEPT("current_dept"),
    CURRENT_DEPT_TREE("current_dept_tree"),
    CURRENT_TENANT("current_tenant");

    private final String code;

    ConditionOperator(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ConditionOperator fromCode(String code) {
        for (ConditionOperator operator : values()) {
            if (operator.code.equals(code)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("未知条件运算符: " + code);
    }
}
