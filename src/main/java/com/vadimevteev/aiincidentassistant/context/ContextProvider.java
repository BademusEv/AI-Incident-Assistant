package com.vadimevteev.aiincidentassistant.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vadimevteev.aiincidentassistant.model.ParsedIncident;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextProvider {

    private static final String SYSTEM_DESCRIPTION = "classpath:knowledge/system-description.md";
    private static final String PAST_INCIDENTS = "classpath:knowledge/past-incidents.json";
    private static final int MAX_RELEVANT_INCIDENTS = 3;

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private String systemDescription;
    private List<PastIncident> pastIncidents;

    @PostConstruct
    void loadKnowledgeBase() {
        try {
            systemDescription = readTextResource(SYSTEM_DESCRIPTION);
            pastIncidents = objectMapper.readValue(
                    resourceLoader.getResource(PAST_INCIDENTS).getInputStream(),
                    new TypeReference<>() {
                    }
            );
            log.info("Loaded {} past incidents into local knowledge base", pastIncidents.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load incident knowledge base", e);
        }
    }

    public IncidentContext findRelevantContext(ParsedIncident incident) {
        Set<String> inputKeywords = incident.keywords();

        List<ScoredIncident> scoredIncidents = pastIncidents.stream()
                .map(pastIncident -> new ScoredIncident(pastIncident, score(pastIncident, inputKeywords)))
                .filter(scoredIncident -> scoredIncident.score() > 0)
                .sorted(Comparator.comparingInt(ScoredIncident::score).reversed()
                        .thenComparing(scoredIncident -> scoredIncident.incident().id()))
                .limit(MAX_RELEVANT_INCIDENTS)
                .toList();

        List<PastIncident> relevantIncidents = scoredIncidents.stream()
                .map(ScoredIncident::incident)
                .toList();
        int maxScore = scoredIncidents.isEmpty() ? 0 : scoredIncidents.get(0).score();

        log.debug("Keyword retrieval matched {} past incident(s) (top score: {})", relevantIncidents.size(), maxScore);
        return new IncidentContext(systemDescription, relevantIncidents, maxScore);
    }

    private int score(PastIncident pastIncident, Set<String> inputKeywords) {
        return (int) pastIncident.keywords().stream()
                .filter(inputKeywords::contains)
                .count();
    }

    private String readTextResource(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private record ScoredIncident(PastIncident incident, int score) {
    }
}
