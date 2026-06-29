# AI Incident Assistant

Production-like Spring Boot service for first-pass incident triage.

This service is designed for an internal trusted perimeter. Caller authentication, tenant isolation, external rate limiting, and billing controls are intentionally out of scope for this demo.

The goal is not to hide everything behind one prompt. The service is built as a small deterministic pipeline around an LLM:

```text
POST /api/incidents/analyze
  -> IncidentController
  -> IncidentService
  -> InputParser
  -> PiiScrubber
  -> ContextProvider
  -> PromptBuilder
  -> IncidentAiClient
  -> ResponseValidator
  -> SecurityPolicyValidator
  -> retry once if invalid
  -> IncidentResponse
```

## Why This Stack

- Java 21
- Spring Boot 3.5.16
- Spring AI 1.1.8
- Spring Web MVC
- Bean Validation
- JUnit 5 + Mockito
- Lombok only for logging and constructor boilerplate

Spring Boot 3.5.x and Spring AI 1.1.x are intentionally chosen over the newest major versions. For a fintech-style service, this is the lower-risk production choice. The Spring AI integration is isolated behind `IncidentAiClient`, so moving to Spring AI 2.x later is a local adapter change.

## API

```http
POST /api/incidents/analyze
Content-Type: application/json
```

Request:

```json
{
  "description": "Customers cannot pay by card. payment-service logs show provider timeouts."
}
```

Response:

```json
{
  "category": "PAYMENT",
  "severity": "HIGH",
  "summary": "Customers cannot complete card payments because payment-service is timing out against the provider.",
  "hypotheses": [
    {
      "title": "External payment provider latency",
      "reasoning": "The current incident mentions payment-service timeouts and matches prior payment provider timeout incidents.",
      "nextSteps": [
        "Check payment provider status and latency metrics",
        "Inspect payment-service timeout logs"
      ]
    }
  ],
  "contextReferences": ["INC-101"],
  "confidence": "HIGH",
  "needsHumanReview": false
}
```

## Retrieval

`ContextProvider` uses deterministic keyword matching over a local knowledge base:

- payment/card/checkout/timeout -> `INC-101`
- db/cpu/reporting/query/504 -> `INC-102`
- smtp/email/notification/top-up -> `INC-103`
- auth/login/token/401 -> `INC-104`

This is intentionally simple and testable. In production, this component is the natural replacement point for embedding search, vector storage, or a fuller RAG pipeline.

Confidence is a deterministic retrieval signal, not model-calibrated probability. HIGH: top context match score >= 3. MEDIUM: score >= 1. LOW: no context matched.

## Structured Output And Recovery

The LLM returns an `IncidentAnalysis` DTO through Spring AI `ChatClient.entity(...)`.

The prompt is split into separate system and user messages:

- system message: role, safety policy, schema, and instruction hierarchy
- user message: system description, retrieved incidents, retry note, and current incident data

The current incident is wrapped as untrusted data. Instructions inside the incident description must not override system instructions.

Before the current incident is added to the prompt, `PiiScrubber` masks common PII-like values in the normalized incident text: card numbers, e-mail addresses, phone numbers, user/account identifiers, IP addresses, passport numbers, and US Social Security numbers. Retrieval keywords are extracted before scrubbing and remain unchanged, so deterministic context selection still works from the original incident terms.

The service still validates the result in Java:

- category and severity must be present
- summary must be non-blank
- hypotheses must be non-empty and capped at 3
- every hypothesis must have 2 to 3 diagnostic next steps
- security-sensitive leakage and prompt-injection phrases are rejected
- response field lengths are capped

If parsing or validation fails, `IncidentService` retries once with a corrective prompt. If the second attempt fails, the API returns `502 Bad Gateway`.

## Configuration

Live LLM calls require:

```shell
export OPENAI_API_KEY=...
```

Optional:

```shell
export OPENAI_MODEL=gpt-4.1-mini
export OPENAI_MAX_TOKENS=900
```

Default model is configured in `src/main/resources/application.yml`.

## Run

```shell
mvn spring-boot:run
```

Example:

```shell
curl -X POST http://localhost:8080/api/incidents/analyze \
  -H 'Content-Type: application/json' \
  -d '{"description":"Customers cannot pay by card. payment-service logs show provider timeouts."}'
```

## Test

```shell
mvn test
```

Tests do not call OpenAI. The AI client is mocked so that the pipeline, retrieval, prompt content, retry behavior, and HTTP error handling stay deterministic.

## Sample Test Incidents

- Card payments fail and payment-service logs show PayGate timeouts -> `PAYMENT`, high severity, context `INC-101`.
- `/payments/create` is slow, api-gateway returns 504, and reporting-service long queries drive DB CPU high -> `DATABASE`, high severity, context `INC-102`.
- Top-up confirmation e-mails are missing while balances are correct and SMTP logs show connection errors -> `NOTIFICATION`, medium severity, context `INC-103`.
- Mobile users cannot log in, auth-service returns 401, and logs mention invalid token signatures -> `AUTHENTICATION`, medium or high severity, context `INC-104`.

## Interview Talking Points

- This is an LLM pipeline, not a prompt wrapper.
- Context selection is deterministic and testable.
- The model only performs classification, summarization, severity assessment, and hypothesis generation.
- Java owns validation, security policy checks, retry, error mapping, configuration, and retrieval.
- The Spring AI adapter is isolated for future upgrades.

## Security Notes

Implemented for this demo:

- system/user prompt separation
- untrusted incident data delimiters
- PII scrubbing before prompt construction
- deterministic context retrieval
- structured output validation
- prompt-injection and leakage phrase rejection
- sanitized retry feedback
- response token cap

Out of scope because the service is assumed to run internally:

- public caller authentication
- tenant isolation
- external rate limiting
- billing/cost controls
- real RAG/vector-store authorization

## Trade-offs

- Retrieval is deterministic keyword matching, not real RAG/vector search.
- No UI, streaming, tool calling, database, or tenant model in v1.
- There is no separate language detector; schema validation, prompt constraints, and retry handle unexpected output format in this compact take-home version.
