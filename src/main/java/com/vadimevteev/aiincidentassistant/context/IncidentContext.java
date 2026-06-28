package com.vadimevteev.aiincidentassistant.context;

import java.util.List;

public record IncidentContext(
        String systemDescription,
        List<PastIncident> relevantIncidents
) {
    public List<String> references() {
        return relevantIncidents.stream()
                .map(PastIncident::id)
                .toList();
    }
}
