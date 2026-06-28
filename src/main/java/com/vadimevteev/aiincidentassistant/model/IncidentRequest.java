package com.vadimevteev.aiincidentassistant.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentRequest(
        @NotBlank(message = "description must not be blank")
        @Size(max = 5_000, message = "description must be at most 5000 characters")
        String description
) {
}
