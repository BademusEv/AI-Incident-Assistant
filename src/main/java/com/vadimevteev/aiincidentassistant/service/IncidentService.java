package com.vadimevteev.aiincidentassistant.service;

import com.vadimevteev.aiincidentassistant.ai.IncidentAiClient;
import com.vadimevteev.aiincidentassistant.ai.IncidentPrompt;
import com.vadimevteev.aiincidentassistant.ai.PromptBuilder;
import com.vadimevteev.aiincidentassistant.context.ContextProvider;
import com.vadimevteev.aiincidentassistant.context.IncidentContext;
import com.vadimevteev.aiincidentassistant.exception.IncidentAnalysisFailedException;
import com.vadimevteev.aiincidentassistant.exception.InvalidAiResponseException;
import com.vadimevteev.aiincidentassistant.model.IncidentAnalysis;
import com.vadimevteev.aiincidentassistant.model.IncidentRequest;
import com.vadimevteev.aiincidentassistant.model.IncidentResponse;
import com.vadimevteev.aiincidentassistant.model.ParsedIncident;
import com.vadimevteev.aiincidentassistant.validation.ResponseValidator;
import com.vadimevteev.aiincidentassistant.validation.SecurityPolicyValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IncidentService {

    private final InputParser inputParser;
    private final ContextProvider contextProvider;
    private final PromptBuilder promptBuilder;
    private final IncidentAiClient incidentAiClient;
    private final ResponseValidator responseValidator;
    private final SecurityPolicyValidator securityPolicyValidator;
    private final int maxAttempts;

    public IncidentService(
            InputParser inputParser,
            ContextProvider contextProvider,
            PromptBuilder promptBuilder,
            IncidentAiClient incidentAiClient,
            ResponseValidator responseValidator,
            SecurityPolicyValidator securityPolicyValidator,
            @Value("${incident-assistant.retry.max-attempts:2}") int maxAttempts
    ) {
        this.inputParser = inputParser;
        this.contextProvider = contextProvider;
        this.promptBuilder = promptBuilder;
        this.incidentAiClient = incidentAiClient;
        this.responseValidator = responseValidator;
        this.securityPolicyValidator = securityPolicyValidator;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public IncidentResponse analyze(IncidentRequest request) {
        ParsedIncident parsedIncident = inputParser.parse(request.description());
        IncidentContext context = contextProvider.findRelevantContext(parsedIncident);

        String validationError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            IncidentPrompt prompt = attempt == 1
                    ? promptBuilder.build(parsedIncident, context)
                    : promptBuilder.buildRetryPrompt(parsedIncident, context, validationError);

            try {
                IncidentAnalysis analysis = incidentAiClient.analyze(prompt);
                responseValidator.validate(analysis);
                securityPolicyValidator.validate(analysis);
                log.info("Incident analysis succeeded on attempt {} with context {}", attempt, context.references());
                return IncidentResponse.from(analysis, context.references());
            } catch (InvalidAiResponseException e) {
                validationError = e.safeRetryMessage();
                log.warn("Incident analysis attempt {} failed with safe validation code {}", attempt, validationError);
                if (attempt == maxAttempts) {
                    throw new IncidentAnalysisFailedException("AI analysis failed after " + maxAttempts + " attempts", e);
                }
            }
        }

        throw new IncidentAnalysisFailedException("AI analysis failed before producing a response");
    }
}
