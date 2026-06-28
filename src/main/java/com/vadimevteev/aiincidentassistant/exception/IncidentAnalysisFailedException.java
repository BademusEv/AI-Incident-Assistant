package com.vadimevteev.aiincidentassistant.exception;

public class IncidentAnalysisFailedException extends RuntimeException {

    public IncidentAnalysisFailedException(String message) {
        super(message);
    }

    public IncidentAnalysisFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
