package com.vadimevteev.aiincidentassistant.model;

import java.util.List;

public record IncidentResponse(
        IncidentCategory category,
        Severity severity,
        String summary,
        List<Hypothesis> hypotheses,
        List<String> contextReferences
) {
    public static IncidentResponse from(IncidentAnalysis analysis, List<String> contextReferences) {
        return new IncidentResponse(
                analysis.category(),
                analysis.severity(),
                analysis.summary(),
                analysis.hypotheses(),
                List.copyOf(contextReferences)
        );
    }
}
