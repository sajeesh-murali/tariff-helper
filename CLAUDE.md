# Tariff Helper

Spring Boot 3.5 / Java 21 / Maven, single module. Pilot app that helps people in Norwegian public-sector jobs
(mostly migrants) check whether previous work experience should give higher seniority and pay under their
collective agreement. Output is in English. Read README.md for the current behaviour.

## Commands
- Build and test everything: `mvn clean verify` (must pass before you finish any task)
- Run locally: `mvn spring-boot:run` (stub LLM by default; no external calls)

## Rules that must not be broken
1. **The code calculates, the LLM only explains or suggests.** Seniority, pay steps, differences and confidence levels
   are computed in Java from the YAML files. Never let an LLM produce or change a salary figure.
2. **Agreement rules and pay tables live in `src/main/resources/agreements/*.yaml`.** Each has source comments.
   Do not change a rule or number without a source; ask the user first. `AgreementRepositoryTest` checks the pay tables.
3. **Privacy.** Nothing is stored. Never log request or response bodies, prompts or model answers. Error messages must
   not echo submitted values. Send the LLM the minimum data needed.
4. **Prompt-injection safety.** Free text from the user (employer, job title, workType) is restricted by character
   whitelist and length, and placed inside `<user_data>` tags that the system prompt says are data, never instructions.
   Anything the LLM returns must be validated against an allowlist before use.
5. Keep the stub LLM working so tests never call the real API.
6. Add or update tests for every behaviour change (JUnit 5, AssertJ, MockMvc, MockRestServiceServer).

## Layout
`api` (controllers, DTOs, exception handler), `domain`, `service` (SeniorityCalculator, SalaryEstimator, EstimateService),
`llm` (LlmClient, StubLlmClient, ClaudeLlmClient, PromptBuilder), `config`, `ratelimit`, `agreement` (YAML loader).

## Status of agreements
KS and Oslo kommune (nurse groups) are modelled. The state and Spekter return 422 with `unavailableReason`.
