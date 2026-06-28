package com.vadimevteev.aiincidentassistant.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        List<String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedRequest(HttpMessageNotReadableException exception) {
        log.warn("Malformed request body");
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed request body"
        );
    }

    @ExceptionHandler(AiProviderException.class)
    public ProblemDetail handleAiProvider(AiProviderException exception) {
        log.warn("AI provider failure");
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI provider is temporarily unavailable"
        );
    }

    @ExceptionHandler(IncidentAnalysisFailedException.class)
    public ProblemDetail handleAnalysisFailed(IncidentAnalysisFailedException exception) {
        log.warn("Incident analysis failed: {}", exception.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                exception.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected application error: {}", exception.getClass().getSimpleName());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected application error"
        );
    }
}
