package com.vadimevteev.aiincidentassistant.controller;

import com.vadimevteev.aiincidentassistant.model.IncidentRequest;
import com.vadimevteev.aiincidentassistant.model.IncidentResponse;
import com.vadimevteev.aiincidentassistant.service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @PostMapping("/analyze")
    public ResponseEntity<IncidentResponse> analyze(@Valid @RequestBody IncidentRequest request) {
        return ResponseEntity.ok(incidentService.analyze(request));
    }
}
