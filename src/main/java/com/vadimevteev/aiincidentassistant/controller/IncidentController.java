package com.vadimevteev.aiincidentassistant.controller;

import com.vadimevteev.aiincidentassistant.model.IncidentRequest;
import com.vadimevteev.aiincidentassistant.model.IncidentResponse;
import com.vadimevteev.aiincidentassistant.service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IncidentResponse> analyze(@Valid @RequestBody IncidentRequest request) {
        log.debug("POST /analyze received, description length {} chars", request.description().length());
        return ResponseEntity.ok(incidentService.analyze(request));
    }
}
