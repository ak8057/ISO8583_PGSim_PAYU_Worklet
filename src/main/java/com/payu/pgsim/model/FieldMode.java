package com.payu.pgsim.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FieldMode {
    STATIC("STATIC"),
    DYNAMIC("DYNAMIC"),
    FROM_REQUEST("FROM_REQUEST"),
    TEMPLATE("TEMPLATE");

    private final String value;

    FieldMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static FieldMode fromString(String value) {
        if (value == null) {
            return STATIC;
        }
        try {
            return FieldMode.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return STATIC;
        }
    }
}
