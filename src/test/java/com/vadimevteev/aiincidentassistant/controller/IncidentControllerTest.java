package com.vadimevteev.aiincidentassistant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vadimevteev.aiincidentassistant.exception.AiProviderException;
import com.vadimevteev.aiincidentassistant.exception.IncidentAnalysisFailedException;
import com.vadimevteev.aiincidentassistant.model.AnalysisConfidence;
import com.vadimevteev.aiincidentassistant.model.Hypothesis;
import com.vadimevteev.aiincidentassistant.model.IncidentCategory;
import com.vadimevteev.aiincidentassistant.model.IncidentResponse;
import com.vadimevteev.aiincidentassistant.model.Severity;
import com.vadimevteev.aiincidentassistant.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IncidentService incidentService;

    @Test
    void returnsBadRequestForBlankDescription() throws Exception {
        mockMvc.perform(post("/api/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"));

        verifyNoInteractions(incidentService);
    }

    @Test
    void returnsAnalysisResponse() throws Exception {
        when(incidentService.analyze(any())).thenReturn(new IncidentResponse(
                IncidentCategory.PAYMENT,
                Severity.HIGH,
                "Customers cannot complete card payments.",
                List.of(new Hypothesis(
                        "External payment provider timeout",
                        "The incident mentions card payments and provider timeouts.",
                        List.of("Check provider latency metrics")
                )),
                List.of("INC-101"),
                AnalysisConfidence.HIGH,
                false
        ));

        mockMvc.perform(post("/api/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestBody("Customers cannot pay by card."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("PAYMENT"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.needsHumanReview").value(false))
                .andExpect(jsonPath("$.contextReferences[0]").value("INC-101"));
    }

    @Test
    void mapsAiProviderFailureToServiceUnavailable() throws Exception {
        when(incidentService.analyze(any()))
                .thenThrow(new AiProviderException("provider unavailable", new RuntimeException("timeout")));

        mockMvc.perform(post("/api/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestBody("Customers cannot pay by card."))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("AI provider is temporarily unavailable"));
    }

    @Test
    void mapsRetryExhaustionToBadGateway() throws Exception {
        when(incidentService.analyze(any()))
                .thenThrow(new IncidentAnalysisFailedException("AI analysis failed after 2 attempts"));

        mockMvc.perform(post("/api/incidents/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RequestBody("Customers cannot pay by card."))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Incident analysis could not be completed"));
    }

    private record RequestBody(String description) {
    }
}
