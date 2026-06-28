package com.vadimevteev.aiincidentassistant.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vadimevteev.aiincidentassistant.ai.IncidentAiClient;
import com.vadimevteev.aiincidentassistant.ai.IncidentPrompt;
import com.vadimevteev.aiincidentassistant.model.Hypothesis;
import com.vadimevteev.aiincidentassistant.model.IncidentAnalysis;
import com.vadimevteev.aiincidentassistant.model.IncidentCategory;
import com.vadimevteev.aiincidentassistant.model.IncidentRequest;
import com.vadimevteev.aiincidentassistant.model.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "spring.ai.openai.api-key=test-api-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class BaseIntegrationTest {

    protected static final String ANALYZE_ENDPOINT = "/api/incidents/analyze";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected IncidentAiClient incidentAiClient;

    @BeforeEach
    void resetAiClient() {
        reset(incidentAiClient);
    }

    protected ResultActions analyze(String description) throws Exception {
        return analyze(new IncidentRequest(description));
    }

    protected ResultActions analyze(IncidentRequest request) throws Exception {
        return mockMvc.perform(post(ANALYZE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    protected ResultActions analyzeRawJson(String json) throws Exception {
        return mockMvc.perform(post(ANALYZE_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    protected ArgumentCaptor<IncidentPrompt> promptCaptor() {
        return ArgumentCaptor.forClass(IncidentPrompt.class);
    }

    protected IncidentAnalysis validAnalysis(IncidentCategory category) {
        return validAnalysis(category, Severity.HIGH);
    }

    protected IncidentAnalysis validAnalysis(IncidentCategory category, Severity severity) {
        return new IncidentAnalysis(
                category,
                severity,
                "Customers are affected by a service degradation.",
                List.of(new Hypothesis(
                        "Likely dependency degradation",
                        "The incident description matches known symptoms for this component.",
                        List.of("Check service health metrics", "Inspect recent error logs")
                ))
        );
    }
}
