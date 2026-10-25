# What the user sees and what happens behind the page

This page is for business readers. It follows a person through the Tariff Helper web page: what they
can choose, what the page does in the background after each step, and what they get back.

> **Note:** this repository contains the back end only. The web page itself is a separate piece of
> work. This page describes how the page is designed to use the three back-end services (called
> *endpoints* below). The wording of buttons and labels may differ in the final page.

## The three services behind the page

| Service | In plain words | When it is used | Uses AI? |
|---|---|---|---|
| **A. List agreements** | "Which agreements and job types do we support?" | Once, when the page opens | No |
| **B. Suggest agreement** | "Which agreement applies to this employer?" | After the person types an employer name (optional step) | Only if fixed rules can't decide |
| **C. Calculate estimate** | "How many years count, and what is the minimum pay?" | When the person presses the final button | Yes, only to write the explanation |

Technical names: A = `GET /api/agreements`, B = `POST /api/agreements/suggest`,
C = `POST /api/estimates`. Full details are in [API.md](API.md).

---

## The journey, step by step

### Step 1. The page opens  →  service A

**What the person sees:** a form with a list of agreements to choose from, and a list of job types
(for example nurse, care assistant).

**What happens behind the page:** the page asks service A for the list. Nothing is calculated, no
personal data is sent, and no AI is used.

**What the person gets:**
- The agreements we support: KS (municipalities) and Oslo kommune.
- The agreements we list but cannot calculate yet: the state (Hovedtariffavtalen i staten) and
  Spekter (hospitals), each with a short reason why. The page can show these greyed out with the reason.
- For each supported agreement, the job types the person can pick.

---

### Step 2. The person tells us who they work for  →  service B (optional)

**What the person can do:** two choices.

- **"I know my agreement"**: pick it from the list. Service B is not needed.
- **"I'm not sure"**: type the employer name (for example "Bydel Grünerløkka") and, if they want,
  their job title (for example "sykepleier").

**What happens behind the page:** the page sends only the employer name and job title to service B.

1. Fixed rules go first. For example, a name ending in "kommune" means the KS agreement, and a
   name starting with "Bydel" means Oslo kommune.
2. Only if no rule fits is the AI asked, and it must pick from our own list or its answer is ignored.
3. If the AI fails, the person is simply told there is no suggestion. They are never shown an error.

**What the person gets:**
- A suggested agreement, with a one-line reason and how sure we are (high, medium or low).
- A suggested job type, if the job title matched one.
- Whether the suggestion came from fixed rules, from AI, or not at all.

The page pre-fills the agreement and job type. The person can accept or change them.
If the suggestion is the state or Spekter, the page tells the person we cannot calculate for that
agreement yet.

---

### Step 3. The person fills in their details

**What the person can enter** (no service is called while they type):

| Section | What they enter | Required? |
|---|---|---|
| Current job | Position percentage (for example 80%) and current yearly salary | Yes |
| Work history | One row per period: type of work, start month, end month, country. Up to 30 rows | Yes (can be empty) |
| Work history (extra) | For private-sector jobs in roles that need education: how related the job was (related, partly related, unrelated) | Only in that case |
| Payslip values | Job code, pay step (lønnstrinn) and seniority date, **typed in**, not uploaded | No |

Types of work they can pick: public service, private service, self-employed, care work (children,
elderly, sick), work in the home, military service.

---

### Step 4. The person presses "Calculate"  →  service C

**What happens behind the page:** the page sends everything to service C. Inside, these things happen in order:

1. **Check the input.** Dates are valid, numbers make sense.
2. **Pick the agreement.** If the person chose one, it is used. If not, service B's logic runs again
   on the employer name.
3. **Check we can calculate for it.** State and Spekter stop here with a clear message.
4. **Pick the job type** (chosen, or worked out from the job code or title).
5. **Count seniority**, month by month, with the agreement's rules. A month covered by two periods counts once.
6. **Look up the minimum pay** in the official pay table, once for "what counts for sure" and once for
   "what counts if the employer accepts the uncertain periods".
7. **Compare with current pay**, after converting it to a full-time figure.
8. **Compare with the payslip**, if the person gave values: what does the employer credit today
   compared with what the rules count?
9. **Work out how sure we are** (high, medium or low), with reasons.
10. **Ask the AI to write the explanation.** It only gets the results and is told not to change any number.

**What the person gets:**

| What | What it means for them |
|---|---|
| Years that count | Seniority that counts for certain, and the higher figure if the employer accepts the uncertain periods |
| Estimated minimum salary | A range (low to high), full-time, per year |
| Difference from current pay | Positive: the minimum is above what they earn now, which is worth raising with the employer. Negative: they already earn above the minimum |
| Breakdown per work period | For each period, how many months counted and why |
| Employer vs rules | Only if payslip values were given: for example "your employer credits about 3 years, the rules count about 12" |
| Plain-English explanation | Written by the AI from the calculated results |
| Confidence level and reasons | How much to trust the result, and why |
| Things to be aware of | Known limits for this agreement |
| Disclaimer | This is an estimate. The employer makes the final decision |

If we assumed the agreement for them, the result says so clearly.

---

## The order, and why

```
Page opens ──► A: list agreements
                    │
   (person not sure which agreement?)
                    │
                    ▼
   Types employer name ──► B: suggest agreement ──► page pre-fills the choice
                    │
                    ▼
   Fills in details (no calls)
                    │
                    ▼
   Presses Calculate ──► C: calculate estimate ──► result shown
```

| Order | Why |
|---|---|
| **A first** | The page cannot show the choices until it knows them. It is free and fast and needs no personal data. |
| **B before C** | Many people do not know their agreement. Suggesting it first lets them see and correct it before they enter a long work history. It avoids a wasted calculation on the wrong agreement. |
| **C last** | It needs everything the person entered. It is the only step that produces the salary result, and the only one that always uses the AI. |
| **B is optional** | If the person already chose an agreement, B is skipped. C can also work out the agreement itself from the employer name, but showing the suggestion early lets the person confirm it. |

---

## What can go wrong, and what the person sees

| Situation | What the person sees |
|---|---|
| Agreement is State or Spekter | A clear message that we cannot calculate for it yet, and why |
| Agreement or job type cannot be worked out | A message listing the options to choose from |
| Something is missing or wrong in the form | A message naming the field (never repeating what they typed) |
| Too many requests in a minute | "Please wait and try again" (limit is 5 per minute per person, to control AI cost) |
| The AI service is down | The calculation is **not** shown without its explanation, and they are asked to try again |

---

## What happens to the person's data

- Nothing is stored. There is no database.
- Nothing the person types is written to logs.
- The AI never receives payslip values. It only gets the conclusion.
- Employer name and job title are sent to the AI only when fixed rules cannot decide the agreement.

More on why: [WHY-WE-BUILT-IT-THIS-WAY.md](WHY-WE-BUILT-IT-THIS-WAY.md).
