package io.github.ayongbox.daos.core.metadata.form;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 联动动作类型。
 */
public enum LinkageActionType {

    SHOW("show"),
    HIDE("hide"),
    REQUIRE("require"),
    OPTIONAL("optional"),
    READONLY("readonly"),
    EDITABLE("editable"),
    ENABLE("enable"),
    DISABLE("disable"),
    SET_VALUE("set_value"),
    CLEAR_VALUE("clear_value"),
    SET_EXPRESSION("set_expression"),
    SET_OPTIONS("set_options"),
    FILTER_OPTIONS("filter_options"),
    SET_REQUIRED_MESSAGE("set_required_message"),
    TRIGGER_VALIDATE("trigger_validate"),
    TRIGGER_FETCH("trigger_fetch");

    private final String code;

    LinkageActionType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static LinkageActionType fromCode(String code) {
        for (LinkageActionType action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未知联动动作: " + code);
    }
}
