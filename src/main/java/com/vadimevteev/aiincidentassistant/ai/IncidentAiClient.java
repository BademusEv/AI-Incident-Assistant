package com.vadimevteev.aiincidentassistant.ai;

import com.vadimevteev.aiincidentassistant.model.IncidentAnalysis;

public interface IncidentAiClient {

    IncidentAnalysis analyze(IncidentPrompt prompt);
}
