package com.vadimevteev.aiincidentassistant.model;

import java.util.List;

public record IncidentResponse(
        IncidentCategory category,
        Severity severity,
        String summary,
        List<Hypothesis> hypotheses,
        List<String> contextReferences,
        AnalysisConfidence confidence,
        boolean needsHumanReview
) {
    public static IncidentResponse from(IncidentAnalysis analysis, List<String> contextReferences, int topMatchScore) {
        AnalysisConfidence confidence = computeConfidence(topMatchScore);
        return new IncidentResponse(
                analysis.category(),
                analysis.severity(),
                analysis.summary(),
                analysis.hypotheses(),
                List.copyOf(contextReferences),
                confidence,
                confidence == AnalysisConfidence.LOW || analysis.category() == IncidentCategory.UNKNOWN
        );
    }

    private static AnalysisConfidence computeConfidence(int topMatchScore) {
        if (topMatchScore >= 3) {
            return AnalysisConfidence.HIGH;
        }
        if (topMatchScore >= 1) {
            return AnalysisConfidence.MEDIUM;
        }
        return AnalysisConfidence.LOW;
    }
}
