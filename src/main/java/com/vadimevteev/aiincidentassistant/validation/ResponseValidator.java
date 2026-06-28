package com.vadimevteev.aiincidentassistant.validation;

import com.vadimevteev.aiincidentassistant.exception.InvalidAiResponseException;
import com.vadimevteev.aiincidentassistant.model.Hypothesis;
import com.vadimevteev.aiincidentassistant.model.IncidentAnalysis;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ResponseValidator {

    private final Validator validator;

    public void validate(IncidentAnalysis analysis) {
        if (analysis == null) {
            throw new InvalidAiResponseException("AI response was null");
        }

        Set<ConstraintViolation<IncidentAnalysis>> violations = validator.validate(analysis);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .collect(Collectors.joining("; "));
            throw new InvalidAiResponseException(message);
        }

        List<Hypothesis> hypotheses = analysis.hypotheses();
        for (int i = 0; i < hypotheses.size(); i++) {
            Hypothesis hypothesis = hypotheses.get(i);
            if (hypothesis.nextSteps().stream().anyMatch(String::isBlank)) {
                throw new InvalidAiResponseException("hypotheses[" + i + "].nextSteps contains a blank step");
            }
        }
    }
}
