package io.github.ayongbox.daos.core.metadata.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 权限动作。动作与资源解耦，由 ResourceType 约束合法组合。
 */
public enum ActionType {

    VIEW("view"),
    CREATE("create"),
    READ("read"),
    UPDATE("update"),
    DELETE("delete"),
    IMPORT("import"),
    EXPORT("export"),
    BULK_UPDATE("bulk_update"),
    SHARE("share"),
    DESIGN("design"),
    PUBLISH("publish"),
    MANAGE_MEMBER("manage_member"),
    SUBMIT("submit"),
    APPROVE("approve"),
    REJECT("reject"),
    WITHDRAW("withdraw"),
    TRANSFER("transfer"),
    ADD_SIGN("add_sign"),
    UPLOAD("upload"),
    DOWNLOAD("download"),
    WRITE("write");

    private final String code;

    ActionType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ActionType fromCode(String code) {
        for (ActionType action : values()) {
            if (action.code.equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未知权限动作: " + code);
    }
}
