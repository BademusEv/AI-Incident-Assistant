package com.vadimevteev.aiincidentassistant.context;

import java.util.List;

public record PastIncident(
        String id,
        String title,
        List<String> keywords,
        String category,
        String summary,
        String rootCause,
        List<String> remediation
) {
}
