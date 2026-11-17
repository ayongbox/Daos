package io.github.ayongbox.daos.core.metadata.form;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 表单组件类型。组件只负责展示与交互，字段语义始终来自元数据。
 */
public enum FormComponent {

    INPUT("input"),
    TEXTAREA("textarea"),
    RICHTEXT("richtext"),
    NUMBER("number"),
    CURRENCY("currency"),
    PERCENT("percent"),
    PASSWORD("password"),

    SELECT("select"),
    MULTI_SELECT("multi_select"),
    RADIO("radio"),
    CHECKBOX("checkbox"),
    SWITCH("switch"),
    CASCADER("cascader"),
    TREE_SELECT("tree_select"),

    DATE("date"),
    DATETIME("datetime"),
    DATE_RANGE("date_range"),
    TIME("time"),
    MONTH("month"),

    MEMBER("member"),
    MEMBER_MULTI("member_multi"),
    DEPARTMENT("department"),
    DEPARTMENT_MULTI("department_multi"),
    ROLE_SELECT("role_select"),

    ATTACHMENT("attachment"),
    IMAGE("image"),
    IMAGE_GROUP("image_group"),
    SIGNATURE("signature"),

    RELATION("relation"),
    SUBFORM("subform"),
    ROLLUP("rollup"),
    FORMULA("formula"),
    AUTO_NUMBER("auto_number"),

    RATING("rating"),
    PROGRESS("progress"),
    LOCATION("location"),
    BARCODE("barcode"),
    SCANNER("scanner"),

    GROUP("group"),
    TAB("tab"),
    GRID("grid"),
    DIVIDER("divider"),
    STATIC_TEXT("static_text"),
    ALERT("alert"),
    DESCRIPTION_LIST("description_list"),

    CREATOR("creator"),
    CREATE_TIME("create_time"),
    MODIFIER("modifier"),
    MODIFY_TIME("modify_time"),
    RECORD_ID("record_id");

    private final String code;

    FormComponent(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public boolean container() {
        return this == GROUP || this == TAB || this == GRID;
    }

    /**
     * 纯布局组件，不绑定字段。
     */
    public boolean layoutOnly() {
        return container() || this == DIVIDER || this == STATIC_TEXT || this == ALERT || this == DESCRIPTION_LIST;
    }

    @JsonCreator
    public static FormComponent fromCode(String code) {
        for (FormComponent component : values()) {
            if (component.code.equals(code)) {
                return component;
            }
        }
        throw new IllegalArgumentException("未知表单组件: " + code);
    }
}
