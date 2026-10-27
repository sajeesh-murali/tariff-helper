# REST API reference

Tariff Helper has three endpoints. The web page uses two, in this order: **Step 1** `POST /api/payslips/parse` (upload
the payslip) and **Step 2** `POST /api/estimates` (the estimate). `GET /api/agreements` is kept for tools and tests; the
page does not show agreements to the person. Base URL when running locally: `http://localhost:8080`.
Request and response bodies are JSON (`Content-Type: application/json`), except the payslip upload, which is
`multipart/form-data`. Dates are `yyyy-MM` (year and month only).

| Method | Path | Purpose | Calls an LLM? | Rate limited? |
|---|---|---|---|---|
| GET | `/api/agreements` | List agreements and job categories. Kept, but not used by the web page | No | No |
| POST | `/api/estimates` | Calculate seniority and minimum pay from a work history | Yes, to write the explanation (and to suggest the agreement if needed) | Yes |
| POST | `/api/payslips/parse` | Read the form values from a payslip PDF and suggest the agreement from the employer, for the person to check | Yes: a small redacted excerpt, then (only if the fixed rules cannot decide the agreement) the employer name and job title | Yes |

Principle behind all of them: **the code calculates, the LLM only explains or suggests.** Seniority, pay steps,
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
| `id` | string | Agreement id. Use it as `agreementId` in `POST /api/estimates`. Currently `ks-hta`, `oslo-kommune`, `spekter`, `staten`. |
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

## 2. `POST /api/estimates`

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
| `agreementId` | no | Max 40 characters. An id from `GET /api/agreements` (the page passes the one from Step 1). If omitted, `employerName` is required and the agreement is suggested from it. |
| `jobCategory` | no | Max 60 characters. An id from that agreement's `jobCategories`. If omitted it is derived (see below). |
| `positionPercent` | yes | Integer 1 to 100. The person's **current** position percentage. |
| `currentAnnualSalary` | yes | Greater than 0, at most 5,000,000. What the person actually earns per year at `positionPercent`. The app converts it to full time. |
| `experiences` | yes | List of 0 to 30 entries (below). |
| `employerName` | conditional | 2 to 100 characters, same character whitelist as the payslip employer name. Required when `agreementId` is missing. |
| `jobTitle` | no | Up to 60 characters, same whitelist. Used to derive `jobCategory`. |
| `stillingskode` | no | Up to 6 digits. From the payslip. Used to derive `jobCategory` (Oslo) and nothing else. |
| `lonnstrinn` | no | 1 to 100. Pay step from the payslip. Used only to work out the years the employer already credits. |
| `ansiennitetsdato` | no | `yyyy-MM`, between 1950-01 and the current month. The seniority date from the payslip. |

**Each entry in `experiences`**

| Field | Required | Rules and meaning |
|---|---|---|
| `kind` | yes | `public_service`, `private_service`, `self_employed`, `care_work`, `home_work`, `military_service`, `au_pair` (case-insensitive). |
| `startDate`, `endDate` | yes | `yyyy-MM`. Both between 1950-01 and the current month. `endDate` must not be before `startDate`. Both months are included. |
| `country` | yes | 2-letter code, for example `NO`. Informational. |
| `relevance` | conditional | `related`, `partly_related`, `unrelated`. Required only for `private_service` when the agreement credits private service by relevance (KS) and the job requires education. |
| `workType` | no | Lowercase letters, digits, underscore, 1 to 60 characters. Context for the explanation only. |
| `positionPercent` | no | 1 to 100. Informational: part-time counts as full time, so it does not change the result. |

The payslip fields (`stillingskode`, `lonnstrinn`, `ansiennitetsdato`) are **never sent to the LLM**. Only the code's
conclusion about them is.

**`screening`: the questions asked before the estimate (optional).** Answers as total time, without dates, beside the
dated `experiences`. Every answer is optional and one with no time is ignored. Part-time work is stated as the time the
person was employed, not converted to full time. Time is `{ "years": 0-60, "months": 0-11 }`. Set `approximate: true` on
an answer the person is not sure of.

```json
"screening": {
  "workInNorway":    { "duration": { "years": 5, "months": 3 }, "selfEmployed": { "years": 1 }, "approximate": false },
  "workAbroad":      { "duration": { "years": 2 } },
  "auPair":          { "duration": { "years": 1 } },
  "militaryService": { "duration": { "years": 1 }, "country": "NO" },
  "childCare":       { "children": 2, "duration": { "years": 2, "months": 6 } },
  "otherHome":       { "type": "care_work", "duration": { "years": 1 } },
  "periodsOverlap":  "unsure"
}
```

| Answer | Counted as |
|---|---|
| `workInNorway`, `workAbroad` | Work. The questions do not ask whether it was public or private, so it is treated as private service with unknown relevance: full where the agreement credits all private service (Oslo) or the position has no education requirement (KS), otherwise **possible** (KS, education required). `country` has no effect. |
| `workInNorway.selfEmployed`, `workAbroad.selfEmployed` | The part of the total that was own firm. Counted as `self_employed` (possible). Cannot be longer than the total (`400`). |
| `auPair` | `au_pair`: earns nothing (see the table above). |
| `militaryService` | `military_service`: counts in full. For a `country` other than `NO` the entry says the rules we found do not name the country and the employer decides; the number is the same. |
| `childCare` | `care_work`, capped by the agreement's care limit. `children` is stored for the form but does not change the result. |
| `otherHome` | `type` is required when there is time: `care_work` (children, elderly or sick people) or `home_work` (other work in the home). Each follows its own row above. |
| `periodsOverlap` | `no`, `yes` or `unsure`. See below. |

**Overlap.** Totals have no dates, so overlap cannot be removed exactly. With `no` the answers are added. With `yes`,
`unsure`, or no answer when there is more than one period (the dated `experiences` count as one more block), the result is
a range: the low end is what is certain if every period overlapped as much as it could (counted months, never below the
largest single period), the high end is everything added up (counted plus possible months). The agreement rules and
limits are the same ones used for dated periods; this adds no rule. The range lowers confidence and the entries say so.

Each summary answer appears in `perExperience` after the dated entries, with `index` continuing from them and a note
that starts with its name, for example `Work in Norway (summary answer, no dates): ...`. Using any answer adds the
caveat "preliminary estimate" and a confidence reason. Enter each period once, either with dates or as a total; a period
given both ways would count twice.

### How the response is decided
`EstimateService.estimate` runs these steps in order. The first failing step ends the request with the error shown.

1. **Payslip date check.** `ansiennitetsdato` outside 1950-01 to now gives `400`.
2. **Choose the agreement.**
   - `agreementId` given: it must exist, else `404`. The confidence reason will say "You chose the agreement".
   - Not given: `employerName` is required (`400` if both are missing). The agreement suggestion logic (section 3) runs and the
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
   - If `screening` has any answer with time, `ScreeningSeniorityCalculator` adds it as described above (the same
     calculator and rules, with overlap handled as a range when it may exist).
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
12. **Caveats.** The job category note (if any) followed by the agreement's caveats, both fixed text from the YAML file,
    and the "preliminary estimate" caveat when `screening` was used.

**How each kind of experience is credited**

| `kind` | KS (job requires education) | KS (no education requirement) | Oslo kommune |
|---|---|---|---|
| `public_service` | counts | counts | counts |
| `military_service` | counts | counts | counts |
| `private_service` | `related` counts, `partly_related` (or missing) is possible, `unrelated` does not count | counts | counts |
| `self_employed` | possible | possible | possible |
| `care_work` | counts, up to 3 years in total | counts, up to 3 years in total | counts, up to 2 years in total |
| `home_work` | does not count | counts, up to 6 years in total | possible only, up to 2 years, sharing the care limit |
| `au_pair` | does not count | does not count | does not count |

`au_pair` earns nothing because the rules we have modelled do not credit it and no source for a rule was found. The
note on the entry says the employer may assess it. It never takes a month away from a period that does count. If you
find the rule and its source, add it to the agreement's YAML file and to `SeniorityCalculator` together.

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
| Any `screening` answer used (total time without dates) | `medium`, with the reason "preliminary estimate" |
| An answer marked `approximate` | `medium` |
| Periods may overlap (`periodsOverlap` is `yes`, `unsure` or missing, with more than one period) | `medium` |
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

## 3. `POST /api/payslips/parse`

### Purpose
Fills in the form of `POST /api/estimates` from an uploaded payslip PDF, so the person does not have to type
the values. The result is only a suggestion: the person checks it in the UI and submits the values to the estimate
endpoint, which validates them again. Nothing in this endpoint calculates seniority or pay.

### Request
`multipart/form-data` with one part named `file`: a PDF with a text layer, at most 2 MB and 5 pages.

```
curl -F "file=@payslip.pdf;type=application/pdf" http://localhost:8080/api/payslips/parse
```

### Response `200`
```json
{ "employerName": "Oslo kommune", "jobTitle": "Sykepleier", "positionPercent": 80,
  "annualSalaryFullTime": 612300, "currentAnnualSalary": 489840,
  "stillingskode": "7936", "lonnstrinn": 45, "ansiennitetsdato": "2015-08",
  "agreement": { "agreementId": "oslo-kommune", "name": "Oslo kommune (own agreement)", "status": "available",
                 "unavailableReason": null, "confidence": "high", "source": "rules",
                 "reason": "The employer name is Oslo kommune or one of its districts (bydeler) or agencies (etater).",
                 "jobCategory": "nurse" },
  "note": "These values were read automatically. Check them against your payslip before using them." }
```

| Field | Read from the payslip | Goes to the estimate form as |
|---|---|---|
| `employerName` | Arbeidsgiver | `employerName` |
| `jobTitle` | Stilling | `jobTitle` |
| `positionPercent` | Stillingsprosent | `positionPercent` |
| `annualSalaryFullTime` | Årslønn i 100 % stilling før skatt, as printed | not sent; shown so the person can compare |
| `currentAnnualSalary` | **Calculated by the application:** `annualSalaryFullTime` × `positionPercent` ÷ 100, whole NOK | `currentAnnualSalary` (the form wants pay at the person's own percentage) |
| `stillingskode` | Stillingskode | `stillingskode` |
| `lonnstrinn` | Lønnstrinn | `lonnstrinn` |
| `ansiennitetsdato` | Registrert ansiennitet (a date), as `yyyy-MM` | `ansiennitetsdato` |

`agreement` is the agreement suggested from `employerName` (and `jobTitle`), to be confirmed by the person. It is
`null` when the employer was not read or neither the rules nor the model could decide; the person then types the
employer name and job title and `POST /api/estimates` works the agreement out from them. `agreement.status` is `placeholder` for `spekter` and `staten` (no rules yet): an estimate
for those returns `422`, so the UI can say so before asking the experience questions. `agreement.source` is `rules`
(fixed rules in code) or `ai` (the model, validated against the list of agreements). `jobCategory` is
set only when the job title matches one of that agreement's job categories.

Any value is `null` when it was not found or did not pass the checks; `currentAnnualSalary` is `null` unless both
`annualSalaryFullTime` and `positionPercent` were read. With `LLM_PROVIDER=stub` everything is always `null`.

### How it works
1. **Read.** PDFBox reads the text layer in memory. The PDF is never stored. Scanned payslips (no text layer) are
   rejected with `400`; they are not supported yet.
2. **Cut down and mask.** Only lines that mention arbeidsgiver, stilling, prosent, årslønn, lønnstrinn, ansiennitet or
   kode, and the two rows below them, are kept (at most 30 lines of 160 characters). Lines with a personal label
   (name, address, account, birth, phone, email) are dropped. National ID numbers, account numbers, IBAN, phone numbers and email addresses are masked.
   If no line is kept, nothing is sent to the LLM and all values are `null`.
3. **Copy out.** The excerpt goes to the LLM inside `<user_data>` tags. The model only copies values; it calculates
   nothing.
4. **Check.** Each value is accepted only if it is in range **and** is written in the excerpt that was sent. Ranges
   are the estimate form's: `employerName` 2 to 100 and `jobTitle` 2 to 60 characters from the same character
   whitelist, `positionPercent` and `lonnstrinn` 1 to 100, `annualSalaryFullTime` 1 to 5,000,000 whole NOK (printed as
   `612 300,00`, `612.300,00` or `612300`), `stillingskode` up to 6 digits, `ansiennitetsdato` between 1950-01 and now.
   A value the model made up, merged from two columns (for example `7936` and `45` into `793645`) or took from an
   injected instruction is dropped.
5. **Suggest the agreement.** From the employer name and job title, as described below. The person confirms or
   changes it.

A name that stands on a kept line without a label cannot be recognised. Keeping the excerpt this small is the main
protection.

### How the agreement is chosen
`AgreementSuggestionService` works in two stages and stops at the first that gives an answer. It runs on the
`employerName` and `jobTitle` read from the payslip, and only when an employer was read.

**Stage A: fixed rules in code (`AgreementMatcher`).** The employer name is lowercased, trimmed and has its spaces
collapsed. The rules are tried in this order and the first match wins:

| # | Employer name | Agreement | Confidence |
|---|---|---|---|
| 1 | contains `oslo kommune`, starts with `bydel `, or contains `sykehjemsetaten` / `velferdsetaten` / `helseetaten` | `oslo-kommune` | high |
| 2 | ends with ` kommune` | `ks-hta` | high |
| 3 | matches `helse ... hf`, ends with ` hf`, or contains `sykehus` / `universitetssykehus` | `spekter` | medium |
| 4 | contains the word `nav`, a word starting `politi` (not `politisk`), or `universitet` / `forsvar` / `kriminalomsorg` / `skatteetaten` | `staten` | medium |

The order matters: `universitetssykehus` is Spekter (rule 3), not the state (rule 4). `Navestad` does not match `nav`
because only the whole word counts.

**Stage B: the LLM, only if no rule matched.** The model gets the employer name, job title and the list of valid
agreement ids, and must answer JSON `{agreementId, confidence, reason}`. The answer is accepted only if all of these hold:
it is a single valid JSON object, `agreementId` is in the allowlist, `confidence` is `high`, `medium` or `low`, and
`reason` is at most 200 characters of plain sentence characters. If anything fails, or the model call throws, the
result is "no suggestion". This stage never returns an error to the caller.

**Job category.** If the job title contains a known keyword (`sykepleier`, `spesialsykepleier`, `helsefagarbeider`,
`barnehagelærer`, `barnehageassistent`, `pleieassistent`, `assistent`, `nurse`, longest keyword first) and that category
exists in the chosen agreement, it is returned as `jobCategory`. Otherwise `null`.

### Errors
| Status | When |
|---|---|
| `400` | No `file` part, not a PDF (checked by content, not by name), corrupt, more than 5 pages, or no text layer |
| `413` | File larger than 2 MB |
| `429` | Rate limit exceeded (shares the budget of the other POST endpoints) |

If the LLM call that reads the payslip fails the endpoint still answers `200` with all values `null`, and if the
agreement suggestion fails `agreement` is `null`, so the person can type the values in.

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

Only the two POST endpoints that can call the LLM are limited. They share one budget.

- Per client address per minute: `RATE_LIMIT_PER_MINUTE` (default 5).
- Whole service per UTC day: `RATE_LIMIT_PER_DAY` (default 500). This caps LLM spend.
- Over a limit: `429` with a `Retry-After` header (seconds).

## Privacy

Nothing is stored. Request and response bodies, prompts and model answers are never logged. Free text (`employerName`,
`jobTitle`, `workType`) is restricted by a character whitelist and placed in `<user_data>` tags that the system prompt
treats as data. The payslip values typed into `POST /api/estimates` are never sent to the LLM. An uploaded payslip PDF
is never stored or sent as a file; only the small masked excerpt described in section 3 can reach the LLM. The
employer name and job title read from it are sent to the LLM only when the fixed rules cannot decide the agreement.
