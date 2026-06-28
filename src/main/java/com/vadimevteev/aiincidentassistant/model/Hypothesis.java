package com.vadimevteev.aiincidentassistant.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record Hypothesis(
        @NotBlank
        String title,

        @NotBlank
        String reasoning,

        @Size(min = 2, max = 3)
        List<@NotBlank String> nextSteps
) {
}
