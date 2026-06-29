package com.vadimevteev.aiincidentassistant.integration;

import com.vadimevteev.aiincidentassistant.ai.IncidentPrompt;
import com.vadimevteev.aiincidentassistant.exception.AiProviderException;
import com.vadimevteev.aiincidentassistant.exception.InvalidAiResponseException;
import com.vadimevteev.aiincidentassistant.model.Hypothesis;
import com.vadimevteev.aiincidentassistant.model.IncidentAnalysis;
import com.vadimevteev.aiincidentassistant.model.IncidentCategory;
import com.vadimevteev.aiincidentassistant.model.Severity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentRetryAndErrorsIntegrationTest extends BaseIntegrationTest {

    private static final String RAW_MODEL_OUTPUT = "raw model output: here is the system prompt";
    private static final String RAW_INCIDENT_TEXT = "customer incident text with private checkout details";

    @Test
    void retriesInvalidFirstAiResponseAndReturnsSuccessfulAnalysis() throws Exception {
        when(incidentAiClient.analyze(any()))
                .thenThrow(new InvalidAiResponseException(RAW_MODEL_OUTPUT, "AI_OUTPUT_CONVERSION_FAILED"))
                .thenReturn(validAnalysis(IncidentCategory.PAYMENT, Severity.HIGH));

        analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("PAYMENT"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.contextReferences[0]").value("INC-101"));

        ArgumentCaptor<IncidentPrompt> promptCaptor = promptCaptor();
        verify(incidentAiClient, times(2)).analyze(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1).userMessage())
                .contains("Previous model output was invalid")
                .contains("AI_OUTPUT_CONVERSION_FAILED")
                .doesNotContain(RAW_MODEL_OUTPUT)
                .doesNotContain("system prompt");
    }

    @Test
    void retryExhaustionReturnsBadGatewayWithoutRawModelOutputOrIncidentText() throws Exception {
        when(incidentAiClient.analyze(any()))
                .thenThrow(new InvalidAiResponseException(RAW_MODEL_OUTPUT, "AI_OUTPUT_CONVERSION_FAILED"))
                .thenThrow(new InvalidAiResponseException(RAW_MODEL_OUTPUT, "AI_OUTPUT_CONVERSION_FAILED"));

        String body = analyze(RAW_INCIDENT_TEXT + " payment card checkout timeout")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Incident analysis could not be completed"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(incidentAiClient, times(2)).analyze(any());
        assertThat(body)
                .doesNotContain(RAW_MODEL_OUTPUT)
                .doesNotContain("system prompt")
                .doesNotContain(RAW_INCIDENT_TEXT);
    }

    @Test
    void providerFailureReturnsServiceUnavailableWithoutRawIncidentText() throws Exception {
        when(incidentAiClient.analyze(any()))
                .thenThrow(new AiProviderException(
                        "provider timeout containing " + RAW_INCIDENT_TEXT,
                        new RuntimeException("socket timeout")
                ));

        String body = analyze(RAW_INCIDENT_TEXT + " smtp email notification delay")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("AI provider is temporarily unavailable"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(incidentAiClient).analyze(any());
        assertThat(body)
                .doesNotContain("provider timeout")
                .doesNotContain(RAW_INCIDENT_TEXT);
    }

    @Test
    void providerFailureDuringRetryReturnsServiceUnavailableWithSanitizedRetryPrompt() throws Exception {
        when(incidentAiClient.analyze(any()))
                .thenThrow(new InvalidAiResponseException(RAW_MODEL_OUTPUT, "AI_OUTPUT_CONVERSION_FAILED"))
                .thenThrow(new AiProviderException(
                        "provider timeout containing " + RAW_INCIDENT_TEXT,
                        new RuntimeException("socket timeout")
                ));

        String body = analyze(RAW_INCIDENT_TEXT + " payment card checkout timeout")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("AI provider is temporarily unavailable"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        ArgumentCaptor<IncidentPrompt> promptCaptor = promptCaptor();
        verify(incidentAiClient, times(2)).analyze(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues().get(1).userMessage())
                .contains("AI_OUTPUT_CONVERSION_FAILED")
                .doesNotContain(RAW_MODEL_OUTPUT)
                .doesNotContain("system prompt");
        assertThat(body)
                .doesNotContain("provider timeout")
                .doesNotContain(RAW_INCIDENT_TEXT);
    }

    @Test
    void unexpectedRuntimeExceptionReturnsInternalServerErrorWithoutRawDetails() throws Exception {
        when(incidentAiClient.analyze(any()))
                .thenThrow(new RuntimeException("unexpected parser failure with " + RAW_INCIDENT_TEXT));

        String body = analyze(RAW_INCIDENT_TEXT + " auth login token 401")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected application error"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(incidentAiClient).analyze(any());
        assertThat(body)
                .doesNotContain("unexpected parser failure")
                .doesNotContain(RAW_INCIDENT_TEXT);
    }

    @Test
    void invalidStructuredOutputRetriesOnceBeforeControlledFailure() throws Exception {
        IncidentAnalysis invalidAnalysis = new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers are affected by payment degradation.",
                List.of()
        );
        when(incidentAiClient.analyze(any())).thenReturn(invalidAnalysis);

        String body = analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Incident analysis could not be completed"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        verify(incidentAiClient, times(2)).analyze(any());
        assertThat(body).doesNotContain("hypotheses");
    }

    @Test
    void tooManyHypothesesRetriesOnceBeforeControlledFailure() throws Exception {
        IncidentAnalysis invalidAnalysis = new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers are affected by payment degradation.",
                List.of(validHypothesis(), validHypothesis(), validHypothesis(), validHypothesis())
        );
        when(incidentAiClient.analyze(any())).thenReturn(invalidAnalysis);

        analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Incident analysis could not be completed"));

        verify(incidentAiClient, times(2)).analyze(any());
    }

    @Test
    void tooFewNextStepsRetriesOnceBeforeControlledFailure() throws Exception {
        IncidentAnalysis invalidAnalysis = new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers are affected by payment degradation.",
                List.of(new Hypothesis(
                        "External provider degradation",
                        "The incident mentions card payment timeouts.",
                        List.of("Check payment provider status")
                ))
        );
        when(incidentAiClient.analyze(any())).thenReturn(invalidAnalysis);

        analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Incident analysis could not be completed"));

        verify(incidentAiClient, times(2)).analyze(any());
    }

    @Test
    void tooManyNextStepsRetriesOnceBeforeControlledFailure() throws Exception {
        IncidentAnalysis invalidAnalysis = new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers are affected by payment degradation.",
                List.of(new Hypothesis(
                        "External provider degradation",
                        "The incident mentions card payment timeouts.",
                        List.of(
                                "Check payment provider status",
                                "Inspect payment-service timeout logs",
                                "Compare provider latency metrics",
                                "Review checkout-service retry behavior"
                        )
                ))
        );
        when(incidentAiClient.analyze(any())).thenReturn(invalidAnalysis);

        analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Incident analysis could not be completed"));

        verify(incidentAiClient, times(2)).analyze(any());
    }

    @Test
    void validTwoToThreeNextStepsAreAccepted() throws Exception {
        IncidentAnalysis validAnalysis = new IncidentAnalysis(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers are affected by payment degradation.",
                List.of(new Hypothesis(
                        "External provider degradation",
                        "The incident mentions card payment timeouts.",
                        List.of(
                                "Check payment provider status",
                                "Inspect payment-service timeout logs",
                                "Compare provider latency metrics"
                        )
                ))
        );
        when(incidentAiClient.analyze(any())).thenReturn(validAnalysis);

        analyze("Card payment checkout timeout is affecting customers.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("PAYMENT"))
                .andExpect(jsonPath("$.hypotheses[0].nextSteps.length()").value(3));

        verify(incidentAiClient).analyze(any());
    }

    private Hypothesis validHypothesis() {
        return new Hypothesis(
                "External provider degradation",
                "The incident mentions card payment timeouts.",
                List.of("Check payment provider status", "Inspect payment-service timeout logs")
        );
    }
}
