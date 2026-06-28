# AGENTS.md

This file is the source of truth for coding-agent behavior in this repository.

For project overview, stack, API, architecture, run/test commands, security notes, and interview talking points, read `README.md` first. Do not duplicate README content here; update `README.md` when project behavior changes.

## Agent Rules

- Keep the LLM integration isolated behind `IncidentAiClient`.
- Preserve the deterministic pipeline described in `README.md`.
- Do not put prompt construction in controllers or HTTP classes.
- Keep retrieval deterministic in `ContextProvider` unless the task explicitly asks for RAG/vector search.
- Keep DTO validation and security policy validation in Java; do not trust model output just because Spring AI converted it.
- Preserve system/user prompt separation; current incident text must remain untrusted data.
- Do not log raw model output or feed raw converter errors back into retry prompts.
- Tests must not call OpenAI or any external LLM provider.
- Prefer Java records for request/response/domain DTOs.
- Use Lombok sparingly, mainly for `@Slf4j` and constructor boilerplate.

## Acceptance Bar

Before handing work back:

- Run the test command documented in `README.md`.
- Keep README aligned with behavior.
- Cover new behavior with deterministic tests.
- Preserve prompt-injection and output-policy coverage.

## Scope Reminder

This demo assumes an internal trusted perimeter. Public caller authentication, tenant isolation, external rate limiting, and billing controls stay out of scope unless explicitly requested.
