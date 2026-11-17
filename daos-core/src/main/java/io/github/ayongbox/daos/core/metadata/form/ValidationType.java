package io.github.ayongbox.daos.core.metadata.form;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 校验规则类型，参数结构见 schema v1 第 4.5 节。
 */
public enum ValidationType {

    REQUIRED("required"),
    UNIQUE("unique"),
    REGEX("regex"),
    LENGTH("length"),
    RANGE("range"),
    COMPARE("compare"),
    EXPRESSION("expression"),
    FILE_SIZE("file_size"),
    FILE_COUNT("file_count"),
    DATE_RANGE("date_range"),
    PHONE("phone"),
    ID_CARD("id_card"),
    CUSTOM("custom");

    private final String code;

    ValidationType(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ValidationType fromCode(String code) {
        for (ValidationType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知校验类型: " + code);
    }
}
