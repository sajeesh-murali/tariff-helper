# Tariff Helper (pilot)

Helps public-sector workers in Norway check whether their previous work experience should give them
a higher salary under their collective agreement (tariffavtale).

**Status:** KS (municipalities) and Oslo kommune (nurse groups only) are implemented. The state and Spekter are
researched but **not modelled**: they are listed, and an estimate request returns `422` with the reason (shown in
`unavailableReason` on `GET /api/agreements`). See "State and Spekter" below.

## How an estimate is produced

1. **The code does the numbers.** It counts seniority month by month using the agreement rules, looks up
   the minimum pay step, and converts the person's current pay to a full-time figure.
2. **Claude only explains.** The prompt contains the rules summary and the calculated results. Claude writes
   a plain-English explanation. It is told not to change any number.

The result is a range: *what counts* up to *what counts if the employer accepts the uncertain periods*.

## KS rules implemented (HTA § 12.1, pay tables from 1 May 2026)

| Experience | Job with education requirement (nurse, barnehagelærer) | Job without (assistant) |
|---|---|---|
| Public service | counts in full | counts in full |
| Private service | counts if related to the position; partly related = uncertain; unrelated = no | counts in full |
| Care work (children, elderly, sick) | up to 3 years in total | up to 3 years in total |
| Work in the home | does not count | up to 6 years in total |
| Military service | counts in full | counts in full |
| Self-employment | uncertain (employer decides) | uncertain (employer decides) |

- Part-time counts as full time for the period, so position percentage does not reduce credit.
- A calendar month is counted once, even if several periods cover it. Paid work takes priority over care and home work.
- Minimum pay steps up at 6, 8, 10 and 16 completed years. The result is the **minimum** (garantilønn); employers may pay more.

Rules and pay tables live in `src/main/resources/agreements/ks-hta.yaml`. `AgreementRepositoryTest` checks every
pay figure and step increase against the published table.

## Oslo kommune rules implemented (Dokument 25, pay tables from 1 May 2026)

Oslo has its own agreement. The seniority rules differ from KS, and pay comes from a pay frame (lønnsramme)
that gives a pay step (lønnstrinn) for each seniority year from 0 to 16.

| Experience | Oslo kommune |
|---|---|
| Public and private service with fixed agreed hours | counts in full, related or not, with or without an education requirement |
| Hourly or extra-help work | 700 hours in a year counts as 1 year (not modelled, shown as a caveat) |
| Care work (children, elderly, sick) | up to 2 years (a further 4 only for positions with special care functions, not assumed) |
| Work in the home | not mentioned in the rules: shown as uncertain, up to 2 years, sharing the care-work limit |
| Military service | counts in full |
| Self-employment | uncertain (employer decides) |

- Available job groups: nurse (stillingskode 58, ramme 2601), specialist nurse, public health nurse and midwife
  (59/60/61, ramme 3001), clinical nurse (94, ramme 3009). Other roles are not offered yet.
- The figures are for the **lowest alternativ** of the ramme. Employers often offer a higher alternativ, which can be
  negotiated at hiring, so the real offer may be higher. Seniority and pay step cannot be negotiated.
- Rules and pay: `src/main/resources/agreements/oslo-kommune.yaml`. `AgreementRepositoryTest` checks every pay year of
  the three ladders against the published tables.
- Per-agreement seniority behaviour is set in the `rules` block of each YAML file (`privateService`, `homeWork`, care and home limits).

## State and Spekter (no estimate, on purpose)

- **State (Hovedtariffavtalen i staten):** pay is agreed individually at hiring, from the duties, education and relevant
  experience. The agreement has no fixed credit rules for private work, care work or work in the home, and seniority on a
  ladder starts at the first day in the position, so any calculation would be guesswork. The employer must review pay
  within 12 months of hiring. The app says this instead of giving a number.
- **Spekter (hospitals):** rules are in Del B § 4.13 "Lønnsansiennitet" and minimum pay is a yearly negotiated table by
  position group (steps at 0, 4, 8, 10, 16 years), but only 2020-2021 tables and the contents list could be read. Pay also
  differs between trusts. To finish it, the current A2 protocol, Del B and the position-group list are needed as PDF files.

## Requirements

- Java 21
- Maven 3.9+

## Run and test

```bash
mvn verify            # unit and integration tests, no external calls
mvn spring-boot:run   # http://localhost:8080 (override with PORT)
```

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/agreements` | Agreements, their status (`available` or `placeholder`) and job categories for the form |
| POST | `/api/estimates` | Submit the form, get an estimate |
| GET | `/actuator/health` | Health check |

### Request

See `requests/sample-estimate.json` (Postman or curl):

```bash
curl -X POST http://localhost:8080/api/estimates \
  -H "Content-Type: application/json" \
  -d @requests/sample-estimate.json
```

- `jobCategory`: an id from `GET /api/agreements`.
- `positionPercent` and `currentAnnualSalary`: the person's **current** job. The salary is what they actually earn per
  year at that percentage; the app converts it to full time.
- Each entry in `experiences`:
  - `kind`: `public_service`, `private_service`, `self_employed`, `care_work`, `home_work`, `military_service`
  - `relevance`: `related`, `partly_related` or `unrelated`. Required only for `private_service` when the job requires education.
  - `startDate`, `endDate`: `yyyy-MM`, between 1950-01 and the current month
  - `country`: 2-letter code. `workType` (optional): lowercase letters, digits, underscore. `positionPercent` (optional): informational.

### Response

All salary figures are annual, full time. Main fields: `countedYears`, `countedYearsIfAllAccepted`,
`estimatedMinimumSalary` (min/max), `differenceFromCurrent` (negative = already above the minimum),
`perExperience` (months counted and why), `explanation`, `caveats`, `disclaimer`.

Errors: `400` invalid input, `404` unknown agreement, `422` agreement without rules yet, `429` too many requests, `502` the AI service failed.

## Postman

Import `postman/tariff-helper.postman_collection.json` (19 requests: KS, Oslo, not-available agreements and error
cases, each with checks on the expected numbers). The `baseUrl` variable defaults to `http://localhost:8080`.
To run the whole collection, start the app with a higher rate limit, otherwise some requests return `429`:

```powershell
$env:RATE_LIMIT_PER_MINUTE = "100"
mvn spring-boot:run
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | 8080 | HTTP port |
| `LLM_PROVIDER` | stub | `stub` (no external calls, canned explanation) or `claude` |
| `ANTHROPIC_API_KEY` | none | Required when `LLM_PROVIDER=claude`. The app refuses to start without it. Never commit it. |
| `CLAUDE_MODEL` | claude-sonnet-5-5 | Claude model for the explanation. A smaller model may be enough; compare on test cases. |
| `RATE_LIMIT_PER_MINUTE` | 5 | Max estimates per client address per minute (`429` with `Retry-After` above this) |
| `RATE_LIMIT_PER_DAY` | 500 | Max estimates for the whole service per UTC day. This caps Claude spend. |
| `RATE_LIMIT_ENABLED` | true | Turn the limiter off (local testing only) |
| `FORWARD_HEADERS` | none | Set to `native` only behind a trusted proxy/load balancer, so limits use the real client address |
| `CORS_ALLOWED_ORIGINS` | empty (closed) | Comma-separated browser origins allowed to call `/api/**`, for example the front end URL |

```bash
export ANTHROPIC_API_KEY=your-key-here
export LLM_PROVIDER=claude
mvn spring-boot:run
```

With the stub, the numbers are real but the explanation is a placeholder. Each Claude call costs API tokens.

## Docker

```bash
docker build -t tariff-helper .
docker run -p 8080:8080 -e LLM_PROVIDER=claude -e ANTHROPIC_API_KEY=your-key-here tariff-helper
```

The image has no key in it. The limiter is in memory and per instance, so with several instances each has its own limits.

## Privacy

- Nothing is stored. No database.
- Request and response bodies, prompts and model answers are never logged (only status codes, timings, token counts).
- Error responses name the invalid field but never echo submitted values.
- `workType` and `country` accept only safe characters, because they are placed in the LLM prompt.
- With `LLM_PROVIDER=claude`, the work history and current pay from the form are sent to Anthropic's API.
  Check Anthropic's data retention terms for your account and tell users before a real pilot.

## Layout

```
com.tariffhelper
├── api         controllers, request/response types, exception handler
├── domain      Agreement, JobCategory, Rules, SalaryStep, results, enums
├── service     SeniorityCalculator, SalaryEstimator, EstimateService
├── llm         LlmClient, StubLlmClient, ClaudeLlmClient, PromptBuilder
├── config      LlmConfig (stub or Claude), WebConfig (rate limiting, CORS)
├── ratelimit   RateLimiter, RateLimitFilter (429 on POST /api/estimates)
└── agreement   loads and validates src/main/resources/agreements/*.yaml
```

## Known gaps in the Oslo rules (not verified)

- The seniority rules come from the 2022-2024 Dok. 25 (chapter 12) and Lektorlaget's guide (numbered § 9.8 in the later
  agreements); a summary of the 2026 settlement reports no change. The 2026-2028 agreement text was not read.
- Helsefagarbeider, barnehage and assistant roles are missing: their pay frames could not be read (PDFs came back
  as binary, one union site blocks automated reading).
- Higher alternativ (negotiated), local supplements and Oslo's stabiliseringstillegg are not included.
- Work in the home and self-employment are not covered by the rules we found, so they are only ever shown as uncertain.

## Known gaps in the KS rules (not verified)

- The final text of the 2026 agreement (mediated May 2026, member vote closed 18 June) was not read. Section 12 is assumed
  unchanged from the 2024-2026 agreement. The pay tables are the ones valid from 1 May 2026.
- How work abroad is treated is not covered by the sources found. The user is told the employer decides.
- Whether the 3-year care limit and the 6-year home-work limit add together is unconfirmed (the code applies both).
- Mapping of helsefagarbeider and assistant positions to education requirement and pay group is an assumption, shown to the user.
- Not modelled: the age-18 start, the 14-hours-a-week threshold for hourly-paid work, documentation.
  These are shown as caveats.
- Some employers apply a seniority change only from the date documents arrive. This limits back pay and is shown as a caveat.

## Next steps

1. Finish Spekter and the missing Oslo job groups (helsefagarbeider, barnehage, assistants): needs the PDFs that could not be read here (see above).
2. Deploy (free option) and test from Postman.
3. Check Claude explanations on known cases; try smaller models.
