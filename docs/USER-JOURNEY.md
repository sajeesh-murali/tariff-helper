# What the user sees and what happens behind the page

This page is for business readers. It follows a person through the Tariff Helper web page: what they
can choose, what the page does in the background after each step, and what they get back.

> **Note:** this repository contains the back end only. The web page itself is a separate piece of
> work. This page describes how the page is designed to use the two back-end services (called
> *endpoints* below). The wording of buttons and labels may differ in the final page.

## The journey in two steps

The person is not shown a list of agreements and does not pick one. The agreement is worked out from their payslip.

| Step | What the person does | Service behind it | Uses AI? |
|---|---|---|---|
| **1. Upload payslip** | Uploads a payslip PDF and checks the values read from it | `POST /api/payslips/parse` | Yes: to copy values out of a small, masked excerpt, and to suggest the agreement only if fixed rules can't decide |
| **2. Estimate** | Describes their work history and presses "Calculate" | `POST /api/estimates` | Yes, only to write the explanation |

Full details are in [API.md](API.md). The back end also has `GET /api/agreements` (agreements and job types). It is
kept for tools and tests, and the page does not use it.

---

## The journey, step by step

### Step 1. The person uploads a payslip  →  `POST /api/payslips/parse`

**What the person sees:** a page asking for their latest payslip as a PDF.

**What the file must be:** a PDF with a text layer (a payslip downloaded from the employer's system, not a
photo or scan), at most 2 MB and 5 pages. Scanned payslips are not supported yet.

**What happens behind the page:** the page sends the PDF to the service. Inside, these things happen in order:

1. **Read.** The text is read from the PDF in memory. The file is never stored.
2. **Cut down and mask.** Only the few lines that carry the values we need (employer, position, percentage, salary,
   pay step, seniority date, job code) are kept. Lines with a name or address are dropped, and ID numbers, account
   numbers, phone numbers and email addresses are masked. Only this small excerpt can go to the AI.
3. **Copy out.** The AI copies the values it can see. It calculates nothing.
4. **Check.** Each value is kept only if it is in a sensible range **and** is written in the excerpt. A value the AI
   made up is dropped.
5. **Work out the current salary.** The application itself calculates what the person earns at their own percentage
   from the printed full-time salary (for example 612,300 × 80% = 489,840). The AI does not.
6. **Suggest the agreement.** Fixed rules go first: a name ending in "kommune" means KS, "Bydel" or "Oslo kommune"
   means Oslo kommune. Only if no rule fits is the AI asked, and it must pick from our own list or its answer is
   ignored.

**What the person gets:**
- Employer, job title, position percentage, full-time salary as printed, current yearly salary (calculated),
  job code (stillingskode), pay step (lønnstrinn) and seniority date. Any value we could not find or could not
  verify is left empty for the person to fill in.
- The agreement and job type we worked out, with a one-line reason and how sure we are. These are shown as part of
  the result, not as a choice.
- A reminder that the values were read automatically and must be checked against the payslip.
- If the agreement is the state or Spekter, the page tells the person at once that we cannot calculate for that
  agreement yet.

**The person checks and corrects.** The form is filled in from the payslip. The person can change any value, or type
it in if it was left empty.

| Section | What they check or enter | Required? |
|---|---|---|
| Employer and job title | Used to work out the agreement and job type | Yes, if the payslip could not be read |
| Current job | Position percentage (for example 80%) and current yearly salary | Yes |
| Payslip values | Job code, pay step (lønnstrinn) and seniority date | No |

If the upload fails (not a PDF, scanned, too big or too many pages), the person is told so and types the values in
instead. If the AI is unavailable, the page still gets an answer with empty values, so the person can type them in.

---

### Step 2. The person describes their work history and gets the estimate  →  `POST /api/estimates`

**What the person sees:** questions about their earlier work, then a "Calculate" button. No service is called while
they fill these in. Enter each period only once, either with dates or as a total.

**A. Questions with total time (no dates).** Quick answers, for people who do not remember exact dates:

| Question | Notes |
|---|---|
| Work in Norway | Total time; how much of it was in their own firm |
| Work abroad | Total time; how much of it was in their own firm |
| Au pair | Total time. Counts for nothing in the rules we have modelled |
| Military service | Total time and country |
| Child care at home | Total time and number of children |
| Other care or home work | Care (children, elderly, sick) or other work in the home, with total time |
| Do any of these periods overlap in time? | Yes, no or not sure |

For each answer the person can mark "approximate" if they are not sure.

**B. Periods with dates.** One row per period: type of work, start month, end month, country. Up to 30 rows. For
private-sector jobs in roles that need education, the person also says how related the job was (related, partly
related, unrelated).

Types of work they can pick: public service, private service, self-employed, care work (children,
elderly, sick), work in the home, military service, au pair.

**What happens when the person presses "Calculate":** the page sends the checked payslip values and the work history
to the service. Inside, these things happen in order:

1. **Check the input.** Dates are valid, numbers make sense.
2. **Pick the agreement.** The agreement found in Step 1 is used. If none was sent, the same agreement-suggestion
   logic as in Step 1 runs on the employer name.
3. **Check we can calculate for it.** State and Spekter stop here with a clear message.
4. **Pick the job type** (worked out from the job code or title).
5. **Count seniority**, month by month, with the agreement's rules. A month covered by two periods counts once.
   Total-time answers (A above) go through the same rules. Because they have no dates, overlap cannot be removed
   exactly: if periods may overlap, the result is a **range** from the largest single period up to everything
   added together.
6. **Look up the minimum pay** in the official pay table, once for "what counts for sure" and once for
   "what counts if the employer accepts the uncertain periods".
7. **Compare with current pay**, after converting it to a full-time figure.
8. **Compare with the payslip**, if the person gave values: what does the employer credit today
   compared with what the rules count?
9. **Work out how sure we are** (high, medium or low), with reasons. Total-time answers, "approximate" answers
   and possible overlap each lower it to at most medium.
10. **Ask the AI to write the explanation.** It only gets the results and is told not to change any number.

**What the person gets:**

| What | What it means for them |
|---|---|
| Years that count | Seniority that counts for certain, and the higher figure if the employer accepts the uncertain periods |
| Estimated minimum salary | A range (low to high), full-time, per year |
| Difference from current pay | Positive: the minimum is above what they earn now, which is worth raising with the employer. Negative: they already earn above the minimum |
| Breakdown per work period | For each period, how many months counted and why. Total-time answers are listed after the dated periods |
| Employer vs rules | Only if payslip values were given: for example "your employer credits about 3 years, the rules count about 12" |
| Plain-English explanation | Written by the AI from the calculated results |
| Confidence level and reasons | How much to trust the result, and why |
| Things to be aware of | Known limits for this agreement. If total-time answers were used, a "preliminary estimate" note |
| Disclaimer | This is an estimate. The employer makes the final decision |

If we assumed the agreement for them, the result says so clearly.

---

## The order, and why

```
Uploads payslip ──► Step 1: read payslip + work out agreement ──► page pre-fills the values
                    │
                    ▼
   Checks and corrects the values, answers the work-history questions (no calls)
                    │
                    ▼
   Presses Calculate ──► Step 2: calculate estimate ──► result shown
```

| Order | Why |
|---|---|
| **Step 1 first** | It saves typing, and the agreement and job type come from the payslip, so the person never has to know which agreement applies. It also avoids a wasted calculation on the wrong agreement. |
| **Step 2 last** | It needs everything the person entered. It is the only step that produces the salary result, and the only one that always uses the AI. |
| **The person always checks** | The values from Step 1 are a suggestion. Step 2 validates them again. |
| **If the payslip cannot be read** | The person types the employer name, job title and current pay. Step 2 works out the agreement from the employer name itself. |

---

## What can go wrong, and what the person sees

| Situation | What the person sees |
|---|---|
| Payslip is not a PDF, is corrupt, has more than 5 pages, or is a scan with no text | A message that the file cannot be read, and the option to type the values in |
| Payslip file is larger than 2 MB | A message that the file is too large |
| Some payslip values could not be found or verified | Those fields are left empty for the person to fill in |
| AI is unavailable while reading the payslip | Empty fields and no suggested agreement; the person types them in |
| Agreement is State or Spekter | A clear message that we cannot calculate for it yet, and why |
| Agreement or job type cannot be worked out | A message asking for the employer name or job title (the back end lists the options in its error) |
| Something is missing or wrong in the form | A message naming the field (never repeating what they typed) |
| Too many requests in a minute | "Please wait and try again" (limit is 5 per minute per person, shared by payslip reading and calculation, to control AI cost) |
| The AI service is down while calculating | The calculation is **not** shown without its explanation, and they are asked to try again |

---

## What happens to the person's data

- Nothing is stored. There is no database. An uploaded payslip is read in memory and thrown away.
- Nothing the person types or uploads is written to logs.
- The AI never receives the whole payslip. It gets only a few lines around the labels we need, with ID, account, phone
  and email numbers masked and name and address lines dropped.
- Payslip values typed into the calculation form (job code, pay step, seniority date) are never sent to the AI.
  Only the code's conclusion about them is.
- Employer name and job title are sent to the AI for the agreement suggestion only when fixed rules cannot decide it.

More on why: [WHY-WE-BUILT-IT-THIS-WAY.md](WHY-WE-BUILT-IT-THIS-WAY.md).
