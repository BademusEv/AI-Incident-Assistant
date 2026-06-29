package com.vadimevteev.aiincidentassistant.validation;

import com.vadimevteev.aiincidentassistant.exception.InvalidAiResponseException;
import com.vadimevteev.aiincidentassistant.model.Hypothesis;
import com.vadimevteev.aiincidentassistant.model.IncidentAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class SecurityPolicyValidator {

    private static final int MAX_SUMMARY_LENGTH = 600;
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_REASONING_LENGTH = 1_000;
    private static final int MAX_NEXT_STEP_LENGTH = 240;

    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "ignore previous instructions",
            "ignore the previous instructions",
            "ignore all previous instructions",
            "system prompt",
            "hidden prompt",
            "hidden instruction",
            "internal instruction",
            "developer message",
            "api key",
            "apikey",
            "secret key",
            "secret value",
            "token value",
            "bearer token value",
            "password is",
            "password:",
            "credential value",
            "credentials are",
            "credentials:",
            "here is the prompt",
            "here are the instructions",
            "i will reveal"
    );

    public void validate(IncidentAnalysis analysis) {
        rejectTooLong("summary", analysis.summary(), MAX_SUMMARY_LENGTH);
        rejectForbiddenContent("summary", analysis.summary());

        for (int i = 0; i < analysis.hypotheses().size(); i++) {
            Hypothesis hypothesis = analysis.hypotheses().get(i);
            validateHypothesis(i, hypothesis);
        }
    }

    private void validateHypothesis(int index, Hypothesis hypothesis) {
        rejectTooLong("hypotheses[" + index + "].title", hypothesis.title(), MAX_TITLE_LENGTH);
        rejectTooLong("hypotheses[" + index + "].reasoning", hypothesis.reasoning(), MAX_REASONING_LENGTH);
        rejectForbiddenContent("hypotheses[" + index + "].title", hypothesis.title());
        rejectForbiddenContent("hypotheses[" + index + "].reasoning", hypothesis.reasoning());

        for (int i = 0; i < hypothesis.nextSteps().size(); i++) {
            String nextStep = hypothesis.nextSteps().get(i);
            rejectTooLong("hypotheses[" + index + "].nextSteps[" + i + "]", nextStep, MAX_NEXT_STEP_LENGTH);
            rejectForbiddenContent("hypotheses[" + index + "].nextSteps[" + i + "]", nextStep);
        }
    }

    private void rejectTooLong(String field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidAiResponseException(
                    field + " exceeds " + maxLength + " characters",
                    "AI_OUTPUT_TOO_LONG"
            );
        }
    }

    private void rejectForbiddenContent(String field, String value) {
        if (value == null) {
            return;
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        boolean forbidden = FORBIDDEN_PHRASES.stream().anyMatch(normalized::contains);
        if (forbidden) {
            log.warn("Security policy violation in LLM output field '{}'", field);
            throw new InvalidAiResponseException(
                    field + " contains forbidden security-sensitive content",
                    "AI_OUTPUT_SECURITY_POLICY_VIOLATION"
            );
        }
    }
}
