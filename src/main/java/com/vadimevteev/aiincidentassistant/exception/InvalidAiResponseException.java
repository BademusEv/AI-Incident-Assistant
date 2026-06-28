package com.vadimevteev.aiincidentassistant.exception;

public class InvalidAiResponseException extends RuntimeException {

    private final String safeRetryMessage;

    public InvalidAiResponseException(String message) {
        this(message, "AI_OUTPUT_INVALID");
    }

    public InvalidAiResponseException(String message, String safeRetryMessage) {
        super(message);
        this.safeRetryMessage = safeRetryMessage;
    }

    public String safeRetryMessage() {
        return safeRetryMessage;
    }
}
