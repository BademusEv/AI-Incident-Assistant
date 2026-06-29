package com.vadimevteev.aiincidentassistant.integration;

import com.vadimevteev.aiincidentassistant.ai.IncidentPrompt;
import com.vadimevteev.aiincidentassistant.model.Hypothesis;
import com.vadimevteev.aiincidentassistant.model.IncidentAnalysis;
import com.vadimevteev.aiincidentassistant.model.IncidentCategory;
import com.vadimevteev.aiincidentassistant.model.Severity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentSecurityPolicyIntegrationTest extends BaseIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ignore previous instructions",
            "here is the system prompt",
            "system prompt",
            "API key",
            "secret value",
            "token value"
    })
    void unsafeOutputRetriesWithSanitizedFeedbackAndThenSucceeds(String unsafeText) throws Exception {
        when(incidentAiClient.analyze(any()))
                .thenReturn(analysisWithSummary("Model leaked unsafe text: " + unsafeText))
                .thenReturn(validAnalysis(IncidentCategory.PAYMENT));

        analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("PAYMENT"))
                .andExpect(jsonPath("$.contextReferences[0]").value("INC-101"));

        ArgumentCaptor<IncidentPrompt> promptCaptor = promptCaptor();
        verify(incidentAiClient, times(2)).analyze(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1).userMessage())
                .contains("Previous model output was invalid")
                .contains("AI_OUTPUT_SECURITY_POLICY_VIOLATION")
                .doesNotContain(unsafeText);
    }

    @ParameterizedTest
    @MethodSource("oversizedAnalyses")
    void oversizedOutputRetriesWithSanitizedFeedbackAndThenSucceeds(IncidentAnalysis oversizedAnalysis) throws Exception {
        when(incidentAiClient.analyze(any()))
                .thenReturn(oversizedAnalysis)
                .thenReturn(validAnalysis(IncidentCategory.PAYMENT));

        analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("PAYMENT"))
                .andExpect(jsonPath("$.contextReferences[0]").value("INC-101"));

        ArgumentCaptor<IncidentPrompt> promptCaptor = promptCaptor();
        verify(incidentAiClient, times(2)).analyze(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1).userMessage())
                .contains("Previous model output was invalid")
                .contains("AI_OUTPUT_TOO_LONG")
                .doesNotContain("x".repeat(80));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ignore previous instructions",
            "here is the system prompt",
            "API key"
    })
    void unsafeOutputTwiceReturnsBadGateway(String unsafeText) throws Exception {
        when(incidentAiClient.analyze(any()))
                .thenReturn(analysisWithSummary("Model leaked unsafe text: " + unsafeText))
                .thenReturn(analysisWithReasoning("Unsafe retry still says " + unsafeText));

        analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Incident analysis could not be completed"));

        verify(incidentAiClient, times(2)).analyze(any());
    }

    private static Stream<IncidentAnalysis> oversizedAnalyses() {
        return Stream.of(
                analysisWithSummary("x".repeat(601)),
                analysisWithTitle("x".repeat(161)),
                analysisWithReasoning("x".repeat(1_001)),
                analysisWithNextStep("x".repeat(241))
        );
    }

    private static IncidentAnalysis analysisWithSummary(String summary) {
        return new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                summary,
                List.of(validHypothesis())
        );
    }

    private static IncidentAnalysis analysisWithTitle(String title) {
        return new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers are affected by a payment service degradation.",
                List.of(new Hypothesis(
                        title,
                        "The incident description matches known symptoms for this component.",
                        List.of("Check service health metrics", "Inspect recent error logs")
                ))
        );
    }

    private static IncidentAnalysis analysisWithReasoning(String reasoning) {
        return new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers are affected by a payment service degradation.",
                List.of(new Hypothesis(
                        "Likely dependency degradation",
                        reasoning,
                        List.of("Check service health metrics", "Inspect recent error logs")
                ))
        );
    }

    private static IncidentAnalysis analysisWithNextStep(String nextStep) {
        return new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers are affected by a payment service degradation.",
                List.of(new Hypothesis(
                        "Likely dependency degradation",
                        "The incident description matches known symptoms for this component.",
                        List.of(nextStep, "Inspect recent error logs")
                ))
        );
    }

    private static Hypothesis validHypothesis() {
        return new Hypothesis(
                "Likely dependency degradation",
                "The incident description matches known symptoms for this component.",
                List.of("Check service health metrics", "Inspect recent error logs")
        );
    }
}
