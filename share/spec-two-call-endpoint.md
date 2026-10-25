# Spec: agreement suggestion + payslip fields + coded confidence

Goal: the person may not know which agreement applies. They give an employer name and job title (and optionally a few
typed-in payslip values). The app suggests the agreement, the code calculates the estimate, and the code computes a
confidence level. Claude is used twice, for two small jobs. It never produces the salary.

## The two Claude calls

### Call 1: suggest the agreement (new, only when code rules cannot decide)
- Input to Claude: a fixed system prompt, the list of allowed agreement ids (`ks-hta`, `oslo-kommune`, `staten`,
  `spekter`) plus `unknown`, and inside `<user_data>`: employer name and job title only.
  NOT sent: pay, work history, dates, payslip values.
- Claude must reply with JSON only: `{"agreementId": "...", "confidence": "high|medium|low|unknown", "reason": "..."}`.
- The code parses it defensively. If it is not valid JSON, the id is not in the allowlist, or the reason is longer
  than 200 characters, treat the result as `unknown`. If the call fails, treat it as `unknown` (never an error to the user).
- Reuse `ClaudeProperties`/the existing RestClient setup. Add `suggest` to the `LlmClient` interface (or a new
  `AgreementSuggester` interface) with a stub implementation that returns `unknown`.

### Call 2: explain the result (exists already)
- Unchanged, except the prompt gets one extra line with the code's conclusion when payslip values were given, for
  example: "The employer currently credits about 2 years; the rules count about 12 years." Never the raw payslip values.

## Rules-based matcher (runs before Call 1, in code)
`AgreementMatcher` with deterministic rules on the normalised employer name (lowercase, trimmed):
- contains `oslo kommune`, starts with `bydel `, or contains `sykehjemsetaten` / `velferdsetaten` / `helseetaten` -> `oslo-kommune`, high
- ends with ` kommune` (and not Oslo) -> `ks-hta`, high
- matches `helse .* hf`, ends with ` hf`, or contains `universitetssykehus` / `sykehus` -> `spekter`, medium
- contains `universitet`, `nav`, `politi`, `forsvar`, `kriminalomsorg`, `skatteetaten` -> `staten`, medium
- no match -> call Claude (Call 1). Return `source: "rules"` or `"ai"` or `"none"`.
Tests must cover each rule and a non-matching name.

## Endpoint 1: `POST /api/agreements/suggest`
Request:
```json
{ "employerName": "Bydel Grünerløkka", "jobTitle": "sykepleier" }
```
- `employerName`: required, 2 to 100 chars, regex `^[\p{L}\p{N} .,&()/'-]+$`.
- `jobTitle`: optional, up to 60 chars, same regex.
Response:
```json
{ "suggestions": [ { "agreementId": "oslo-kommune", "confidence": "high",
    "reason": "...", "jobCategory": "nurse" } ], "source": "rules" }
```
- `jobCategory` is set only when the title matches one of that agreement's job categories (simple keyword map:
  sykepleier/nurse -> nurse, spesialsykepleier -> specialist_nurse, barnehagelærer -> kindergarten_teacher,
  helsefagarbeider -> health_care_worker, barnehageassistent -> kindergarten_assistant, assistent/pleieassistent ->
  care_assistant). Only return ids that exist in that agreement.
- Empty `suggestions` when nothing matches. Same rate limiting as estimates. Never log the employer name.

## Endpoint 2: extend `POST /api/estimates`
New optional request fields (all validated, none required):
- `employerName`, `jobTitle` (same rules as above)
- `stillingskode`: digits only, up to 6
- `lonnstrinn`: integer 1 to 100
- `ansiennitetsdato`: `yyyy-MM`, not in the future, not before 1950-01
- `agreementId` and `jobCategory` become optional. If `agreementId` is missing, run the matcher/Call 1 using
  `employerName`; if still unknown, return 400 "agreementId or employerName is required" / 422 "could not determine
  the agreement" listing the available agreements. If `jobCategory` is missing, derive it from `stillingskode` (Oslo:
  58 -> nurse, 59/60/61 -> specialist_nurse, 94 -> clinical_nurse) or `jobTitle`; if still unknown, return 400 listing the agreement's categories.
  Never silently guess when the result is not high confidence: the response must show what was assumed.

New response fields:
```json
{
  "agreementSuggestion": { "agreementId": "oslo-kommune", "confidence": "high", "source": "rules", "assumed": true },
  "confidence": { "level": "medium", "reasons": ["Two periods depend on the employer's decision"] },
  "creditedVsCounted": { "creditedYears": 2.0, "countedYears": 12.0, "basis": "lonnstrinn" }
}
```
- `agreementSuggestion` is present only when the agreement was not given explicitly.
- `creditedVsCounted` is present only when the payslip values allow it (see below), otherwise null.

## Credited seniority from payslip values (code only)
- Add an optional `step` (lønnstrinn) number to each Oslo ladder step in `oslo-kommune.yaml`, from the NSF tables
  valid 1 May 2026 (the step number is public, the NOK already there):
  - 2601 (nurse): years 0:23, 1:24, 2:25, 3:26, 6:27, 7:28, 8:29, 9:30, 10:31, 12:32, 13:33, 14:34, 15:35, 16:36
  - 3001 (specialist): 0:28, 2:32, 4:33, 9:34, 10:35, 12:37, 15:39, 16:42
  - 3009 (clinical): 0:36, 2:40, 4:41, 9:42, 10:43, 12:45, 15:47, 16:50
  `AgreementRepositoryTest` must check these against the above.
- If `lonnstrinn` is given and the job group has `step` values: creditedYears = the lowest `fromYears` whose step equals
  `lonnstrinn` (basis `lonnstrinn`). If the trinn is not on the ladder, set a confidence reason and skip.
- Else if `ansiennitetsdato` is given: creditedYears = whole months from that date to now / 12 (basis `ansiennitetsdato`).
- KS has no step numbers yet, so for KS only `ansiennitetsdato` works.
- Add a reason when creditedYears is more than 1 year below countedYears ("Your payslip suggests fewer years than the
  rules count") and when it is above countedYears ("Your employer already credits more than we could count").

## Confidence (code only, never taken from Claude)
- `high`: agreement given by the user or matched by rules with high confidence, no uncertain months, no payslip conflict.
- `medium`: uncertain months exist (range min != max), or the agreement match is medium confidence, or the job group was inferred.
- `low`: agreement came from Claude (source `ai`) with confidence below high, or the payslip step could not be reconciled.
- Always list `reasons` in plain English. Do not present the level as a probability.

## Out of scope
No payslip upload or OCR. No storage. No change to KS/Oslo rules. State and Spekter stay 422.

## Done when
- `mvn clean verify` passes, including new tests: matcher rules, Call 1 parsing/validation (malformed JSON, unknown id,
  injection text in employer name), endpoint validation, estimate with missing agreementId, creditedVsCounted for Oslo
  (trinn 26 -> 3 years), confidence levels, rate limiting on the new endpoint.
- README and the Postman collection (`postman/`) updated with the new endpoint and fields.
- `CLAUDE.md` rules still hold (no logging of bodies, allowlist validation of LLM output).
