package com.vadimevteev.aiincidentassistant.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    @JsonCreator
    public static Severity fromJson(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
