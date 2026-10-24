# REST API reference

Tariff Helper has three endpoints. Base URL when running locally: `http://localhost:8080`.
All request and response bodies are JSON (`Content-Type: application/json`). Dates are `yyyy-MM` (year and month only).

| Method | Path | Purpose | Calls an LLM? | Rate limited? |
|---|---|---|---|---|
| GET | `/api/agreements` | List agreements and job categories for the form dropdowns | No | No |
| POST | `/api/agreements/suggest` | Suggest which agreement applies from the employer name | Only if the fixed rules cannot decide | Yes |
| POST | `/api/estimates` | Calculate seniority and minimum pay from a work history | Yes, to write the explanation (and to suggest the agreement if needed) | Yes |

Principle behind all three: **the code calculates, the LLM only explains or suggests.** Seniority, pay steps,
differences, credited years and confidence levels are computed in Java from `src/main/resources/agreements/*.yaml`.
The LLM never produces or changes a number, and anything it returns is checked against an allowlist before use.

---

## 1. `GET /api/agreements`

### Purpose
Gives the front end what it needs to build the form: which agreements exist, whether an estimate is possible for
each, and which job categories can be chosen. Internal rules and pay tables are not exposed.

### Request
No body, no parameters.

### How the response is decided
The list is every YAML file loaded by `AgreementRepository` at startup, mapped one-to-one (`AgreementSummary.from`).
Nothing is calculated and no LLM is involved.

### Response `200`
A JSON array. One element per agreement:

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Agreement id. Use it as `agreementId` in the other two endpoints. Currently `ks-hta`, `oslo-kommune`, `spekter`, `staten`. |
| `name` | string | Display name, for example `KS Hovedtariffavtale (municipalities)`. |
| `sector` | string | `municipal`, `municipal-oslo`, `health-trusts` or `state`. |
| `status` | string | `available` (estimates work) or `placeholder` (listed, but `POST /api/estimates` returns `422`). |
| `unavailableReason` | string or null | Why a `placeholder` agreement cannot be estimated. Null when `available`. |
| `jobCategories` | array | Choices for `jobCategory`. Each is `{ "id": "nurse", "label": "Nurse (sykepleier)" }`. Empty for placeholders. |

Example (shortened):

```json
[
  {
    "id": "ks-hta",
    "name": "KS Hovedtariffavtale (municipalities)",
    "sector": "municipal",
    "status": "available",
    "unavailableReason": null,
    "jobCategories": [
      { "id": "nurse", "label": "Nurse (sykepleier)" },
      { "id": "care_assistant", "label": "Care assistant (pleie- or omsorgsassistent)" }
    ]
  },
  {
    "id": "staten",
    "name": "Hovedtariffavtalen i staten (state)",
    "sector": "state",
    "status": "placeholder",
    "unavailableReason": "Pay is agreed individually at hiring ...",
    "jobCategories": []
  }
]
```

---

## 2. `POST /api/agreements/suggest`

### Purpose
Answers "which collective agreement applies to this employer?" for people who do not know. The front end can call
it as the person types the employer name, then pre-select the agreement and job category.

### Request

```json
{ "employerName": "Bydel Grünerløkka", "jobTitle": "sykepleier" }
```

| Field | Required | Rules |
|---|---|---|
| `employerName` | yes | 2 to 100 characters, not blank |
| `jobTitle` | no | up to 60 characters |

Both fields allow only letters (any language), digits, spaces and `. , & ( ) / ' -`. This whitelist stops markup or
instructions from reaching the LLM prompt.

### How the response is decided
`AgreementSuggestionService` works in two stages and stops at the first that gives an answer.

**Stage 1: fixed rules in code (`AgreementMatcher`).** The employer name is lowercased, trimmed and has its spaces
collapsed. The rules are tried in this order and the first match wins:

| # | Employer name | Agreement | Confidence |
|---|---|---|---|
| 1 | contains `oslo kommune`, starts with `bydel `, or contains `sykehjemsetaten` / `velferdsetaten` / `helseetaten` | `oslo-kommune` | high |
| 2 | ends with ` kommune` | `ks-hta` | high |
| 3 | matches `helse ... hf`, ends with ` hf`, or contains `sykehus` / `universitetssykehus` | `spekter` | medium |
| 4 | contains the word `nav`, a word starting `politi` (not `politisk`), or `universitet` / `forsvar` / `kriminalomsorg` / `skatteetaten` | `staten` | medium |

The order matters: `universitetssykehus` is Spekter (rule 3), not the state (rule 4). `Navestad` does not match `nav`
because only the whole word counts.

**Stage 2: the LLM, only if no rule matched.** The model gets the employer name, job title and the list of valid
agreement ids, and must answer JSON `{agreementId, confidence, reason}`. The answer is accepted only if all of these hold:
it is a single valid JSON object, `agreementId` is in the allowlist, `confidence` is `high`, `medium` or `low`, and
`reason` is at most 200 characters of plain sentence characters. If anything fails, or the model call throws, the
result is "no suggestion". This stage never returns an error to the caller.

**Job category.** If the job title contains a known keyword (`sykepleier`, `spesialsykepleier`, `helsefagarbeider`,
`barnehagelærer`, `barnehageassistent`, `pleieassistent`, `assistent`, `nurse`, longest keyword first) and that category
exists in the chosen agreement, it is returned as `jobCategory`. Otherwise `null`.

### Response `200`

```json
{
  "suggestions": [
    {
      "agreementId": "oslo-kommune",
      "confidence": "high",
      "reason": "The employer name is Oslo kommune or one of its districts (bydeler) or agencies (etater).",
      "jobCategory": "nurse"
    }
  ],
  "source": "rules"
}
```

| Field | Meaning |
|---|---|
| `suggestions` | Zero or one entries today. Empty when nothing matched. |
| `suggestions[].agreementId` | An id from `GET /api/agreements`. May be a `placeholder` agreement (`spekter`, `staten`). |
| `suggestions[].confidence` | `high`, `medium` or `low`. |
| `suggestions[].reason` | Plain-English reason. Fixed text for rules, validated model text for `ai`. |
| `suggestions[].jobCategory` | Job category id derived from `jobTitle`, or `null`. |
| `source` | `rules` (code), `ai` (the LLM, validated) or `none` (no match, `suggestions` is empty). |

A suggestion for `spekter` or `staten` is still only a suggestion: an estimate for those agreements returns `422`.

### Errors
`400` invalid input (field names only, never the values), `429` rate limit.

---

## 3. `POST /api/estimates`

### Purpose
The main endpoint. Given a person's work history and current pay, it works out how many years of seniority count under
the agreement, the minimum pay that seniority gives, and how that compares with what the person earns now.

### Request
See `requests/sample-estimate.json` and `requests/sample-estimate-employer.json`.

```json
{
  "agreementId": "ks-hta",
  "jobCategory": "nurse",
  "positionPercent": 80,
  "currentAnnualSalary": 480000,
  "experiences": [
    { "country": "NO", "kind": "public_service", "startDate": "2015-08", "endDate": "2019-06" },
    { "country": "PL", "kind": "private_service", "relevance": "related", "workType": "hospital_nurse",
      "startDate": "2019-09", "endDate": "2022-05" }
  ]
}
```

**Top-level fields**

| Field | Required | Rules and meaning |
|---|---|---|
| `agreementId` | no | Max 40 characters. An id from `GET /api/agreements`. If omitted, `employerName` is required and the agreement is suggested from it. |
| `jobCategory` | no | Max 60 characters. An id from that agreement's `jobCategories`. If omitted it is derived (see below). |
| `positionPercent` | yes | Integer 1 to 100. The person's **current** position percentage. |
| `currentAnnualSalary` | yes | Greater than 0, at most 5,000,000. What the person actually earns per year at `positionPercent`. The app converts it to full time. |
| `experiences` | yes | List of 0 to 30 entries (below). |
| `employerName` | conditional | 2 to 100 characters, same whitelist as the suggest endpoint. Required when `agreementId` is missing. |
| `jobTitle` | no | Up to 60 characters, same whitelist. Used to derive `jobCategory`. |
| `stillingskode` | no | Up to 6 digits. From the payslip. Used to derive `jobCategory` (Oslo) and nothing else. |
| `lonnstrinn` | no | 1 to 100. Pay step from the payslip. Used only to work out the years the employer already credits. |
| `ansiennitetsdato` | no | `yyyy-MM`, between 1950-01 and the current month. The seniority date from the payslip. |

**Each entry in `experiences`**

| Field | Required | Rules and meaning |
|---|---|---|
| `kind` | yes | `public_service`, `private_service`, `self_employed`, `care_work`, `home_work`, `military_service` (case-insensitive). |
| `startDate`, `endDate` | yes | `yyyy-MM`. Both between 1950-01 and the current month. `endDate` must not be before `startDate`. Both months are included. |
| `country` | yes | 2-letter code, for example `NO`. Informational. |
| `relevance` | conditional | `related`, `partly_related`, `unrelated`. Required only for `private_service` when the agreement credits private service by relevance (KS) and the job requires education. |
| `workType` | no | Lowercase letters, digits, underscore, 1 to 60 characters. Context for the explanation only. |
| `positionPercent` | no | 1 to 100. Informational: part-time counts as full time, so it does not change the result. |

The payslip fields (`stillingskode`, `lonnstrinn`, `ansiennitetsdato`) are **never sent to the LLM**. Only the code's
conclusion about them is.

### How the response is decided
`EstimateService.estimate` runs these steps in order. The first failing step ends the request with the error shown.

1. **Payslip date check.** `ansiennitetsdato` outside 1950-01 to now gives `400`.
2. **Choose the agreement.**
   - `agreementId` given: it must exist, else `404`. The confidence reason will say "You chose the agreement".
   - Not given: `employerName` is required (`400` if both are missing). The suggest logic from endpoint 2 runs and the
     top suggestion is used. It is flagged `assumed: true` in `agreementSuggestion`. If nothing is suggested the answer is
     `422` with `availableAgreements`.
3. **Is the agreement modelled?** `status: placeholder` (`staten`, `spekter`) gives `422` with the `unavailableReason`.
4. **Choose the job category.** In this order: the given `jobCategory` (must belong to the agreement, else `400` listing
   the options), then the `stillingskode` (Oslo: 58 nurse, 59/60/61 specialist nurse, 94 clinical nurse), then keywords in
   `jobTitle`. If none works, `400` lists the options. The source is remembered because a derived category lowers confidence.
5. **Validate experiences.** Dates must be 1950-01 to now. A missing `relevance` where it is required gives `400` naming the
   entry number (`experiences[2].relevance ...`), never the values.
6. **Count seniority (`SeniorityCalculator`).** Works in whole calendar months:
   - Each period gets a credit type from its `kind` and the agreement's rules (table below).
   - A month covered by several periods is counted once, by the strongest type (order: full, possible, paid-not-credited,
     care, home, home-possible, not credited). Paid work beats care and home work.
   - Care work and home work are then capped by the agreement's yearly limits, in entry order.
   - Result: **counted months** (certain) and **possible extra months** (count only if the employer accepts them).
7. **Look up minimum pay (`SalaryEstimator`).** `completedYears = months / 12` (rounded down). The step used is the one with
   the largest `fromYears` not above that. This is done twice: for counted months (gives `min`) and for counted plus
   possible months (gives `max`).
8. **Compare with current pay.** `currentFullTimeSalary = currentAnnualSalary * 100 / positionPercent`, rounded to whole
   kroner. `differenceFromCurrent = minimum pay - currentFullTimeSalary`, for both ends of the range.
9. **Credited seniority from the payslip (`CreditedSeniorityCalculator`).**
   - `lonnstrinn` is used if the pay ladder has step numbers (Oslo): credited years = the lowest seniority year with that
     step. A step not on the ladder gives no comparison.
   - Otherwise `ansiennitetsdato` is used: whole months from that date to now, divided by 12.
   - KS has no step numbers in its ladders yet, so for KS only `ansiennitetsdato` works.
10. **Confidence (`ConfidenceAssessor`).** Computed in code, see the table below.
11. **Explanation.** The LLM gets the rules summary and the calculated results and writes `explanation`. It is told not to
    change any number. With `LLM_PROVIDER=stub` the explanation is a fixed placeholder. If the LLM call fails the whole
    request returns `502`, because a salary figure without its explanation is not shown.
12. **Caveats.** The job category note (if any) followed by the agreement's caveats, both fixed text from the YAML file.

**How each kind of experience is credited**

| `kind` | KS (job requires education) | KS (no education requirement) | Oslo kommune |
|---|---|---|---|
| `public_service` | counts | counts | counts |
| `military_service` | counts | counts | counts |
| `private_service` | `related` counts, `partly_related` (or missing) is possible, `unrelated` does not count | counts | counts |
| `self_employed` | possible | possible | possible |
| `care_work` | counts, up to 3 years in total | counts, up to 3 years in total | counts, up to 2 years in total |
| `home_work` | does not count | counts, up to 6 years in total | possible only, up to 2 years, sharing the care limit |

"Possible" means it goes into `countedYearsIfAllAccepted` but not `countedYears`. Limits come from the `rules` block of
each YAML file.

**How the confidence level is decided**

Start at `high`, then lower it:

| Condition | Effect |
|---|---|
| Agreement matched by fixed rules with medium confidence | `medium` |
| Agreement came from the LLM | `medium`, and `low` if the model's own confidence was not `high` |
| At least one period only counts if the employer accepts it | `medium` |
| Job category taken from `stillingskode` or `jobTitle` | `medium` |
| `lonnstrinn` not on the pay ladder | `low` |
| Payslip credits more than 1 year fewer than counted | `medium` |
| Payslip credits more than counted | `medium` |
| Agreement chosen by the person, or matched by rules with high confidence, and none of the above | `high` |

The level is a label, not a probability. `confidence.reasons` always lists the reasons in plain English.

### Response `200`
All salary figures are annual and for a full-time (100%) position, in NOK.

```json
{
  "agreementApplied": "KS Hovedtariffavtale (municipalities)",
  "rulesValidFrom": "2026-05-01",
  "jobCategory": "Nurse (sykepleier)",
  "salaryGroup": "201",
  "currentFullTimeSalary": 600000,
  "countedYears": 7.58,
  "countedYearsIfAllAccepted": 7.58,
  "estimatedMinimumSalary": { "min": 523600, "max": 523600 },
  "differenceFromCurrent": { "min": -76400, "max": -76400 },
  "perExperience": [
    { "index": 0, "countedMonths": 47, "possibleExtraMonths": 0, "note": "Public-sector service counts in full." }
  ],
  "explanation": "...",
  "caveats": ["..."],
  "disclaimer": "This is an estimate, not legal or financial advice. Your employer makes the final decision on seniority and salary.",
  "agreementSuggestion": null,
  "confidence": { "level": "high", "reasons": ["You chose the agreement", "No period depends on the employer's decision"] },
  "creditedVsCounted": null
}
```

(Numbers above only illustrate the shape.)

| Field | Meaning |
|---|---|
| `agreementApplied` | Name of the agreement used. |
| `rulesValidFrom` | Date the pay tables apply from. |
| `jobCategory` | Label of the job category used. |
| `salaryGroup` | Pay ladder used (for example `201`, or an Oslo ramme such as `2601`). |
| `currentFullTimeSalary` | The person's current pay converted to 100%. |
| `countedYears` | Seniority that counts for certain, in years with 2 decimals. |
| `countedYearsIfAllAccepted` | Seniority if the employer also accepts every uncertain period. Equal to `countedYears` when nothing is uncertain. |
| `estimatedMinimumSalary.min / max` | The agreement's minimum (garantilønn) for `countedYears` and for `countedYearsIfAllAccepted`. Employers may pay more. |
| `differenceFromCurrent.min / max` | Minimum pay minus current full-time pay. **Positive:** the minimum is above current pay. **Negative:** the person already earns above the minimum. |
| `perExperience[]` | One entry per submitted period, same order. `index` is the position in the request, `countedMonths` and `possibleExtraMonths` are the months it contributes, `note` is fixed plain-English text saying why (including overlap and limit effects), not model output. |
| `explanation` | Plain-English text from the LLM. The only field the model writes. |
| `caveats` | Known limits and assumptions for this agreement and job category. |
| `disclaimer` | Fixed text. |
| `agreementSuggestion` | `null` when `agreementId` was given. Otherwise `{ agreementId, confidence, source, assumed: true }`: the agreement the estimate is based on, because the person did not choose it. |
| `confidence` | `{ level: high/medium/low, reasons: [...] }`, from code. |
| `creditedVsCounted` | `null` unless the payslip values allowed a comparison. Otherwise `{ creditedYears, countedYears, basis }` where `basis` is `lonnstrinn` or `ansiennitetsdato`. `creditedYears` is what the employer currently credits, `countedYears` is what the rules count. If `countedYears` is higher, the person may be entitled to a higher step. |

### Errors

| Status | When |
|---|---|
| `400` | Validation failed (body has `errors`: field name to message), body missing or not valid JSON, neither `agreementId` nor `employerName`, `jobCategory` invalid or not derivable (message lists the options), date out of range, missing `relevance` |
| `404` | `agreementId` does not exist |
| `422` | Agreement has no rules yet (`spekter`, `staten`), or no agreement could be determined from `employerName` (body has `availableAgreements`) |
| `429` | Rate limit exceeded |
| `502` | The LLM call failed |

---

## Errors common to all endpoints

Errors use RFC 7807 problem JSON (`application/problem+json`) with `status` and `detail`.

- Error messages name the field or entry number but **never echo submitted values**.
- Unknown paths give `404`, wrong HTTP method `405`, wrong content type `415`.
- Anything unexpected gives `500` with a generic message. Only the exception type is logged.

```json
{ "status": 400, "detail": "Validation failed", "errors": { "positionPercent": "must be less than or equal to 100" } }
```

## Rate limiting

Only the two POST endpoints are limited, because they can call the LLM. They share one budget.

- Per client address per minute: `RATE_LIMIT_PER_MINUTE` (default 5).
- Whole service per UTC day: `RATE_LIMIT_PER_DAY` (default 500). This caps LLM spend.
- Over a limit: `429` with a `Retry-After` header (seconds).

## Privacy

Nothing is stored. Request and response bodies, prompts and model answers are never logged. Free text (`employerName`,
`jobTitle`, `workType`) is restricted by a character whitelist and placed in `<user_data>` tags that the system prompt
treats as data. Payslip values are never sent to the LLM.
