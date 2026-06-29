package com.vadimevteev.aiincidentassistant.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentAnalysisJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesCategoryAndSeverityCaseInsensitively() throws Exception {
        String json = """
                {
                  "category": "payment",
                  "severity": "high",
                  "summary": "Card payments are timing out.",
                  "hypotheses": [
                    {
                      "title": "Provider latency",
                      "reasoning": "The incident mentions card payment timeouts.",
                      "nextSteps": [
                        "Check provider latency metrics",
                        "Inspect payment-service timeout logs"
                      ]
                    }
                  ]
                }
                """;

        IncidentAnalysis analysis = objectMapper.readValue(json, IncidentAnalysis.class);

        assertThat(analysis.category()).isEqualTo(IncidentCategory.PAYMENT);
        assertThat(analysis.severity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void deserializesCategoryAndSeverityWithMixedCaseAndWhitespace() throws Exception {
        String json = """
                {
                  "category": " database ",
                  "severity": "Medium",
                  "summary": "Reporting queries are slow.",
                  "hypotheses": [
                    {
                      "title": "Database contention",
                      "reasoning": "The incident mentions slow reporting queries.",
                      "nextSteps": [
                        "Check database CPU metrics",
                        "Inspect slow query logs"
                      ]
                    }
                  ]
                }
                """;

        IncidentAnalysis analysis = objectMapper.readValue(json, IncidentAnalysis.class);

        assertThat(analysis.category()).isEqualTo(IncidentCategory.DATABASE);
        assertThat(analysis.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void rejectsUnknownCategoryValue() {
        String json = """
                {
                  "category": "billing",
                  "severity": "high",
                  "summary": "Unexpected category should fail conversion.",
                  "hypotheses": [
                    {
                      "title": "Unknown category",
                      "reasoning": "The category is not in the allowed enum.",
                      "nextSteps": [
                        "Return a valid category",
                        "Retry with the allowed schema"
                      ]
                    }
                  ]
                }
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, IncidentAnalysis.class))
                .isInstanceOf(ValueInstantiationException.class);
    }
}
