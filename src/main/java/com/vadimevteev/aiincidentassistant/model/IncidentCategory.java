package com.vadimevteev.aiincidentassistant.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum IncidentCategory {
    PAYMENT,
    NOTIFICATION,
    DATABASE,
    AUTHENTICATION,
    INFRASTRUCTURE,
    UNKNOWN;

    @JsonCreator
    public static IncidentCategory fromJson(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
