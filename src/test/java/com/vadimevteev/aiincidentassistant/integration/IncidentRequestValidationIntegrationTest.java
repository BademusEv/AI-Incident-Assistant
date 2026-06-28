package com.vadimevteev.aiincidentassistant.integration;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentRequestValidationIntegrationTest extends BaseIntegrationTest {

    @Test
    void rejectsBlankDescriptionBeforeCallingAi() throws Exception {
        analyzeRawJson("{\"description\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("description: description must not be blank"));

        verifyNoInteractions(incidentAiClient);
    }

    @Test
    void rejectsMissingDescriptionBeforeCallingAi() throws Exception {
        analyzeRawJson("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("description: description must not be blank"));

        verifyNoInteractions(incidentAiClient);
    }

    @Test
    void rejectsNullDescriptionBeforeCallingAi() throws Exception {
        analyzeRawJson("{\"description\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("description: description must not be blank"));

        verifyNoInteractions(incidentAiClient);
    }

    @Test
    void rejectsTooLongDescriptionBeforeCallingAi() throws Exception {
        analyze("x".repeat(5_001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0]").value("description: description must be at most 5000 characters"));

        verifyNoInteractions(incidentAiClient);
    }

    @Test
    void rejectsMalformedJsonBeforeCallingAi() throws Exception {
        analyzeRawJson("{\"description\":\"unterminated")
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(incidentAiClient);
    }
}
