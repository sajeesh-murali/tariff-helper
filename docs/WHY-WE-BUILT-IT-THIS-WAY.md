# Why Tariff Helper is built this way

This page is for business readers. It explains three design choices and the reasons behind them.
No technical knowledge is needed.

In short:

| Choice | What we do | What we avoid |
|---|---|---|
| Who calculates the salary? | Our own program, using fixed rules | Asking an AI to work out the salary |
| Salary slip | Person types in 3 optional values | Uploading the salary slip itself |
| Agreement rules | Stored in our project, with sources | Asking an AI to search for them each time |

---

## 1. Why the AI does not decide the salary

**What happens today**

1. The person enters their work history.
2. Our program counts the years of experience that count under the agreement, month by month.
3. Our program looks up the minimum pay in the official pay table.
4. Only then is an AI asked to **explain** the result in plain English. It is told not to change any number.

**Why we do it this way**

- **The answer must be correct, and it must be the same every time.**
  The same input always gives the same result. An AI can give a slightly different answer each time
  it is asked, and it can sound sure of itself while being wrong.
- **People may act on the number.** Someone might ask their employer for a raise based on it.
  A wrong figure that sounds convincing is worse than no figure.
- **We can check it.** Every pay figure in our tables is tested against the published agreement.
  We cannot test an AI's judgement in the same way.
- **We can explain it.** For every period of work, the result shows how many months counted and why.
  An AI cannot reliably show its working.
- **It is cheaper and faster.** The calculation costs nothing to run. The AI is only used for the
  wording.

**What the AI is good for here:** turning a table of results into a friendly explanation, and
suggesting which agreement may apply when the employer name is unclear. Even then, its suggestion
must be one from our own fixed list, or it is ignored.

---

## 2. Why we do not ask people to upload their salary slip

**What we ask for instead:** up to three optional values from the slip, typed in by the person:
job code (stillingskode), pay step (lønnstrinn) and seniority date (ansiennitetsdato).
These are only used to compare "what your employer credits you" with "what the rules say".

**Why we do it this way**

- **A salary slip is very personal.** It holds a name, a national ID number, bank details, tax and
  pay. We would be responsible for handling all of it.
- **We only need a few values.** Collecting less data means less that can go wrong.
- **Nothing is stored.** The app has no database. What the person enters is used to produce the
  answer and is then gone.
- **Nothing is sent to the AI.** The three slip values never leave our program. The AI only gets the
  conclusion, for example: "the employer credits about 3 years; the rules count about 12 years".
- **Slips look different everywhere.** Reading a slip automatically (a scan or a PDF) can misread a
  number, and a misread number would silently change the result. A person typing three values
  can see and correct them.
- **It builds trust.** People are more willing to try a tool that does not ask for their documents.

---

## 3. Why the agreement rules are stored in our project and not searched by the AI

**What we do:** the rules and pay tables for each agreement are kept in plain files in our project
(`src/main/resources/agreements/`). Each rule has a comment saying which paragraph of the agreement
it comes from. Automated tests check every pay figure against the published table.

**Why we do it this way**

- **An AI can make things up.** If asked to look up a rule or a pay figure, it may give an old
  table, a rule from a different agreement, or something that looks right but does not exist.
- **Pay tables change.** They are updated every year (the current ones are valid from 1 May 2026).
  With our approach a person updates one file, the tests show if anything is off, and the change is
  recorded with a date and an author. An AI would silently use whatever it found that day.
- **Every rule has a source.** When someone asks "where does this come from?", we can point to the
  paragraph. We cannot do that with an AI's search.
- **Experts can review it.** A union or HR specialist can read the file and confirm it is right.
  They cannot review what an AI will decide to say tomorrow.
- **It is predictable and cheap.** No internet search, no extra cost per request, and it keeps
  working if the AI service is down.
- **Safer against manipulation.** Web pages and free-text input can contain hidden instructions
  meant to trick an AI. Fixed files that we control cannot.

**The trade-off.** Someone has to read each agreement and add it to the project. That is why only
two agreements are done so far (KS and Oslo kommune, nurse groups). The state and Spekter return
"not available" rather than a guess. We think an honest "not yet" is better than a number we cannot
stand behind.

---

## The principle behind all three

> **The program calculates. The AI explains.**
> Anything that decides money comes from checked rules. The AI never produces or changes a salary figure.

The result is a **minimum** pay estimate. Employers may pay more, and the final decision is always
the employer's.

For technical details see the main [README](../README.md) and [API reference](API.md).
