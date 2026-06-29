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

import java.util.regex.Pattern;

@Slf4j
@Service
public class IncidentService {

    private static final Pattern PII_PLACEHOLDER = Pattern.compile("\\[[A-Z_]+\\]");
    private static final long MAX_RETRY_DELAY_MS = 30_000L;

    private final InputParser inputParser;
    private final PiiScrubber piiScrubber;
    private final ContextProvider contextProvider;
    private final PromptBuilder promptBuilder;
    private final IncidentAiClient incidentAiClient;
    private final ResponseValidator responseValidator;
    private final SecurityPolicyValidator securityPolicyValidator;
    private final int maxAttempts;
    private final long initialRetryDelayMs;

    public IncidentService(
            InputParser inputParser,
            PiiScrubber piiScrubber,
            ContextProvider contextProvider,
            PromptBuilder promptBuilder,
            IncidentAiClient incidentAiClient,
            ResponseValidator responseValidator,
            SecurityPolicyValidator securityPolicyValidator,
            @Value("${incident-assistant.retry.max-attempts:2}") int maxAttempts,
            @Value("${incident-assistant.retry.initial-delay-ms:0}") long initialRetryDelayMs
    ) {
        this.inputParser = inputParser;
        this.piiScrubber = piiScrubber;
        this.contextProvider = contextProvider;
        this.promptBuilder = promptBuilder;
        this.incidentAiClient = incidentAiClient;
        this.responseValidator = responseValidator;
        this.securityPolicyValidator = securityPolicyValidator;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialRetryDelayMs = Math.max(0, initialRetryDelayMs);
    }

    public IncidentResponse analyze(IncidentRequest request) {
        ParsedIncident parsedIncident = inputParser.parse(request.description());
        parsedIncident = piiScrubber.scrub(parsedIncident);
        // Re-derive keywords from the scrubbed text, but strip PII placeholders first so tokens
        // like "email" (from [EMAIL]) or "card" (from [CARD_NUMBER]) don't pollute retrieval.
        String scrubbedDescription = parsedIncident.normalizedDescription();
        String descriptionWithoutPlaceholders = PII_PLACEHOLDER.matcher(scrubbedDescription).replaceAll(" ");
        parsedIncident = new ParsedIncident(scrubbedDescription, inputParser.parse(descriptionWithoutPlaceholders).keywords());
        IncidentContext context = contextProvider.findRelevantContext(parsedIncident);

        String validationError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            IncidentPrompt prompt = promptBuilder.build(parsedIncident, context, attempt == 1 ? null : validationError);

            try {
                IncidentAnalysis analysis = incidentAiClient.analyze(prompt);
                responseValidator.validate(analysis);
                securityPolicyValidator.validate(analysis);
                log.info("Incident analysis succeeded on attempt {} with context {} (top match score: {})", attempt, context.references(), context.topMatchScore());
                return IncidentResponse.from(analysis, context.references(), context.topMatchScore());
            } catch (InvalidAiResponseException e) {
                validationError = e.safeRetryMessage();
                log.warn("Incident analysis attempt {} failed with safe validation code {}", attempt, validationError);
                if (attempt == maxAttempts) {
                    throw new IncidentAnalysisFailedException("AI analysis failed after " + maxAttempts + " attempts", e);
                }
                sleepWithBackoff(attempt);
            }
        }

        throw new IncidentAnalysisFailedException("AI analysis failed before producing a response");
    }

    private void sleepWithBackoff(int attempt) {
        if (initialRetryDelayMs <= 0) {
            return;
        }
        long delay = Math.min(initialRetryDelayMs * (1L << Math.min(attempt - 1, 30)), MAX_RETRY_DELAY_MS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IncidentAnalysisFailedException("Analysis interrupted during retry backoff", e);
        }
    }
}
