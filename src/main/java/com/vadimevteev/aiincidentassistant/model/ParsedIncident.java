package com.vadimevteev.aiincidentassistant.model;

import java.util.Set;

public record ParsedIncident(
        String normalizedDescription,
        Set<String> keywords
) {
}
