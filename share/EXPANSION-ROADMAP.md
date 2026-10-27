# Tariff Helper: how to reach more public-sector workers

**Audience:** business, partnerships and product people.
**Purpose:** show what Tariff Helper covers today, what is missing, and what we need from you (documents, contacts,
decisions) so that more people can use it. More covered agreements and job types means more users, and more users
is the basis for revenue.

Everything below marked **(verify)** is our understanding from public sources and must be confirmed with the
agreement owners or a union/employer contact before we build on it.

---  

## 1. Where we are today

| Sector / agreement | Who it covers | Status | What users get |
|---|---|---|---|
| **KS (HTA)** | Municipalities and county municipalities | Modelled | Seniority, minimum pay step, difference from current pay |
| **Oslo kommune** | Oslo city and its districts, agencies | Modelled, **nurse groups only** | Same, for nurse, specialist nurse, public health nurse, midwife, clinical nurse |
| **Hovedtariffavtalen i staten** | Universities, police, NAV, defence, prisons, tax authority and similar | **Not modelled** | A clear message, no number |
| **Spekter (hospitals)** | Health trusts (helseforetak) | **Not modelled** | A clear message, no number |

Job groups inside the modelled agreements today:

- **KS:** nurse, specialist nurse, kindergarten teacher, health care worker (helsefagarbeider), kindergarten
  assistant, care assistant.
- **Oslo:** nurse (ramme 2601), specialist nurse / public health nurse / midwife (3001), clinical nurse (3009).

In short: we cover **municipal health, care and kindergarten work** well, and almost nothing else. Teachers, engineers,
administrators, IT staff, doctors, police, university staff, hospital staff and most other public roles get no estimate.

**Why we say "no estimate" instead of guessing:** a wrong salary figure hurts the user (they may accept a bad offer or
argue a wrong case) and hurts our credibility. Our rule is that the code calculates only from published agreement
rules and pay tables, each with a source. This also keeps us honest as we grow. Every new agreement needs a source
document, not an assumption.

---

## 2. The biggest gap: Spekter and the State

These two are the biggest untapped groups, and they are different problems.

### 2.1 Spekter, hospitals (health trusts)

**Why it matters:** hospitals employ a very large number of nurses, health care workers, doctors, physios,
biomedical scientists, radiographers, cleaners and administrators, and many of them are internationally recruited.
These are exactly the people who benefit most from a check of foreign experience.

**What blocks us:** the seniority rules ("Lønnsansiennitet", Del B § 4.13) and the yearly negotiated minimum-pay
tables (steps at 0, 4, 8, 10 and 16 years, by position group) could not be read in usable form. Actual pay also
differs between trusts.

**What we need from business (all as text-readable PDF or Word files):**

1. The current Spekter **Hovedavtalen / Overenskomst område 10 (Del A and Del B)**, including § 4.13.
2. The latest **A2 protocol** (the yearly minimum-pay tables by position group).
3. The **list of position groups** (stillingsgrupper) and which job titles belong to each.
4. **Local agreements per health trust** (Helse Sør-Øst, Vest, Midt-Norge, Nord, and the trusts under them) where pay
   differs from the national minimum. Ask which trusts matter most for our users first.
5. A contact (HR, union representative or employer organisation) who can answer "how is experience from abroad and
   from private employers counted?" **(verify)**

**Likely design:** model national minimum pay by position group first, label it clearly as a minimum, then add trust
tables where we can get them.

### 2.2 The State (Hovedtariffavtalen i staten)

**Why it matters:** universities, police, NAV, the tax authority, defence, prisons, courts and ministries together
employ a very large and varied workforce, including many administrative, academic and technical roles.

**What blocks us:** this is a *different kind* of agreement. Pay is agreed **individually at hiring** based on duties,
education and relevant experience, within a range for the position code. There are no fixed rules that turn
private work, care work or home work into seniority years. A single "correct" number does not exist.

**What we need from business:**

1. The **pay plan (Vedlegg 1, lønnsplan)**: position codes (stillingskoder) with their pay ranges.
2. The state **salary policy / retention and recruitment guidelines** that employers (universities, NAV, police) use
   when placing new hires in the range. These are often published per employer.
3. Confirmation of how "relevant experience" is weighed in practice **(verify)**.

**Likely design:** do not calculate a single figure. Instead show the **pay range for the position code**, explain
that pay is negotiated, list the employer's own salary-policy criteria, and prepare a **negotiation/review aid**: the
employer must review pay within 12 months of hiring, and the user can bring a written summary of their experience.
This is still valuable and still honest.

---

## 3. Other sectors that look public to the user

A migrant worker does not think in "tariff areas". They think "I work for the public sector". The table lists what
else exists in Norway. **All rows are (verify)**: we need confirmation of scope, size and whether the agreements are
public.

| Area | Typical employers / roles | Agreement owner | Priority |
|---|---|---|---|
| **KS, remaining job groups** | Teachers (also under special agreements), engineers, planners, administrators, technical, cleaning, kitchen, bus/transport, fire and rescue, social workers, physio, doctors in municipal service | KS, unions (Fagforbundet, NSF, Utdanningsforbundet, Delta, etc.) | **Very high** (same agreement we already have, only more job groups) |
| **Oslo kommune, remaining groups** | Helsefagarbeider, kindergarten, assistants, teachers, administration | Oslo kommune | **High** |
| **Spekter område 10 (hospitals)** | Hospital staff | Spekter | **High** |
| **State** | Universities, NAV, police, defence | Staten / Fornyings- og forvaltningsdepartementet (verify current owner) | **High** |
| **Other Spekter areas** | Public-owned companies, for example transport, airports, broadcasting, ports (verify list) | Spekter | Medium |
| **Virke HUK and similar** | Church, culture, non-profit and private health/care providers delivering public services, often public-funded | Virke, KA | Medium (not strictly public but often seen as such) |
| **Private providers under municipal contract** | Care homes, ambulance, nurse agencies | NHO Geneo, others | Later (needs a separate decision: are they in scope?) |
| **Universities/colleges academic staff** | Researchers, lecturers | State + special academic pay rules | Medium (verify how much is covered by the state agreement vs separate rules) |

**Business question to answer first:** which of these groups can we reach cheaply (union partners, municipal HR,
employment services, nurse recruitment agencies, integration offices) and which have most migrant workers?
This should drive the order, more than how easy the agreement is to model.

---

## 4. Covering more job types inside agreements we already have

This is the **fastest win** because the rules engine already works. We only need pay tables and a clear mapping.

### 4.1 KS: complete the job list

KS (HTA) has a chapter structure where job groups sit in different pay tables and, for some roles, in other chapters
or special agreements. What we need:

1. The **full KS HTA** (current version, with the 2026 settlement text; we have not read the final text yet).
2. The **pay tables for all chapters** (kapittel 4 and 5, the position-code tables).
3. The **mapping of stillingskoder to pay group and education requirement**. Today this mapping is partly an
   assumption (helsefagarbeider, assistants), and it is shown to the user as an assumption.
4. Special agreements for **teachers (SFS 2201 and similar)**, **physiotherapists**, **doctors in municipal service**
   and **leaders** **(verify exact names)**.

Job families to add, in rough order of how many people they affect **(verify with employment statistics)**:

1. Teachers (grunnskole, videregående)
2. Social workers, child welfare workers
3. Engineers, planners, technical staff
4. Administrative and office roles
5. Cleaning, kitchen, caretaker (often assistant-level, low education requirement, where "work in the home" and
   "private service" rules matter most for migrants)
6. Bus drivers, fire and rescue, other operational roles
7. Doctors and other academic health roles

### 4.2 Oslo kommune: complete the nurse-only list

Missing: helsefagarbeider, kindergarten, assistants and non-health roles. We could not read their pay frames; the
PDFs came back as binary and one union site blocked automated reading. Oslo's own **Dokument 25** (current version)
and the **lønnsrammer** per position code are what we need.

### 4.3 Higher pay alternatives and local supplements

Both KS and Oslo have things we do not model: Oslo's higher "alternativ" levels, local supplements, stabilisation
supplement, and local pay negotiations (KS kapittel 4). Users sometimes see a number higher than our minimum and
wonder why. Adding **typical ranges** (not just the minimum) would make the tool more useful and more accurate to
real payslips. This needs a source, for example official pay statistics (SSB) for the occupation.

---

## 5. What the product must do differently as we grow

The calculation engine is general: agreement rules and pay tables are read from YAML files, and adding an agreement
means adding a file and tests. But three kinds of agreement need new capability, not just new data.

| Agreement type | Example | What the product needs |
|---|---|---|
| **Rule-based** (what we have) | KS, Oslo | Rules plus pay table. Mostly data work. |
| **Range-based** | State | A "pay range for this position code" result, no single minimum, plus a negotiation aid. New result type in the API. |
| **Table per employer** | Spekter trusts | Pay tables keyed by trust as well as position group. New data dimension. |
| **Mixed (several chapters or special agreements)** | KS teachers, doctors | One employer, several agreements, chosen by job group. |

**Other product improvements that raise reach:**

- **Language.** The output is English today. Migrant users also read Polish, Arabic, Ukrainian, Tagalog, Lithuanian,
  Spanish, Swedish and others, and Norwegian (bokmål). Multi-language output is a presentation change only, because
  all numbers come from code, not from the language model.
- **Foreign experience.** Rules for how work abroad counts are unclear in the KS and Oslo sources. We should get
  an official answer. This is the single biggest question for our main user group.
- **Payslip upload (done for text PDFs) and contract upload (later, careful).** Payslip upload is built
  (`POST /api/payslips/parse`): the PDF is read in memory and never stored, only a few redacted lines reach the model,
  and the person checks every value. Still open: scanned payslips (OCR) and contracts. Any design must keep the
  principle that nothing is stored.
- **Letter/e-mail template.** After the estimate, produce a polite Norwegian draft that the user can send to HR
  asking for a seniority review, with the calculation attached. Practical, and the point where users feel the value.
- **Authorisation and qualifications.** Many nurses and health workers wait for authorisation or recognition of
  education before they can be placed in the right group. A short, correct explanation or link would help
  (this is guidance, not a calculation) **(verify the right authority and process)**.
- **Front end.** The product is currently an API. A simple, accessible web form (mobile first) is needed for
  business and partner use.
- **Freshness.** Pay tables change each year (KS and Oslo from 1 May, Spekter after the A2 negotiations, State after
  the main settlement). We need a **yearly update routine** with a named owner, and a visible "valid from" date in the
  result.

---

## 6. What we need from the business side (checklist)

| # | What | From whom | Unlocks |
|---|---|---|---|
| 1 | Spekter område 10, Del A/B, current version (text-readable) | Spekter or a union | Spekter hospitals |
| 2 | Latest A2 protocol and position-group list | Spekter, NSF, Fagforbundet or a trust HR contact | Spekter pay tables |
| 3 | Local pay tables for the largest trusts | Trust HR | Trust-specific figures |
| 4 | State HTA pay plan (Vedlegg 1) and employers' salary policies | Staten, a university HR, NAV | State ranges |
| 5 | Full KS HTA and pay tables for all chapters, 2026 settlement | KS | More KS job groups |
| 6 | Special agreements (teachers, doctors, physio, leaders) | KS, Utdanningsforbundet, Legeforeningen, Fysioterapeutene | More KS job groups |
| 7 | Oslo Dokument 25 and pay frames for remaining roles | Oslo kommune, unions | More Oslo job groups |
| 8 | A written answer on how foreign work experience is credited | KS, Oslo, Spekter, State, unions | Better caveats and fewer "uncertain" results |
| 9 | Employment statistics: size of each sector and job family | SSB, NAV, partner | Priority order |
| 10 | A legal/data-protection review of what we may send to an AI provider | Legal / DPO | Safe public launch |
| 11 | Partner list: unions, integration offices, recruitment agencies | Business | Distribution |

**Every document should come with its source and date.** We keep source comments in each agreement file, and
tests check the pay tables against the published ones. A rule or number with no source will not go in.

---

## 7. Suggested order

| Phase | Goal | Main work |
|---|---|---|
| **1. Widen what we have** | More KS and Oslo job groups | Pay tables and mappings for teachers, social workers, admin, technical, helsefagarbeider and assistants in Oslo. Mostly data and tests. |
| **2. Spekter** | Open hospitals | National minimum pay by position group, seniority rules, then trusts. |
| **3. State** | Open universities, NAV, police and others | Range-based result and negotiation aid. |
| **4. Reach** | More users | Languages, web front end, HR letter template, partner integration. |
| **5. Other areas** | Public companies, Virke, others | Decide scope, then add case by case. |

Phases 1 and 2 give the biggest coverage for the least new engineering. Phase 3 needs a product decision because the
result is not a single number.

---

## 8. Revenue: options to discuss

These are options for the business team to assess, not forecasts. We have no usage or pricing data yet.

- **Free for individuals, paid for organisations.** Unions, recruitment agencies, integration offices and
  municipalities license the tool for members or candidates.
- **B2B for HR.** Employers use it to check offers for fairness and consistency, which is cheaper than a mistake and a
  later back-pay claim.
- **Premium extras.** Report with the calculation, HR letter template, multi-language pack, review of results by an adviser.
- **API.** Recruitment platforms and job portals embed the estimate.

**Constraints that protect trust (and therefore revenue):** show a minimum, not a promise; say what is assumed;
never give a figure without a source; store nothing; give users an honest "we cannot calculate this" when that is
the truth.

More sectors and job families make every one of these options stronger. A union or agency will only pay if most of
their members are covered, so **breadth of coverage is the product's main commercial lever.**

---

## 9. Risks to manage

- **Wrong or outdated figures.** Mitigated by sourced YAML files, tests against published tables, and a yearly update owner.
- **Legal advice line.** The tool informs; it does not give legal advice. Disclaimers stay in every result.
- **Privacy.** Experience and pay data are personal. We store nothing; with an external AI provider some data is sent.
  A data-protection review is needed before a wide launch.
- **Agreement scope disputes.** Some employers are in unclear areas (company owned by municipality, church, foundation).
  The suggestion step shows its confidence and the user can choose manually.
- **Unions and employers.** They may see the tool as helpful or as a threat. Early talks and sources from them help.

---

*Technical note for developers: a new agreement is a YAML file in `src/main/resources/agreements/` (rules, job
categories, pay steps, source comments) plus tests in `AgreementRepositoryTest` and the estimate tests. Range-based
(State) and per-trust (Spekter) results need changes in `domain`, `service` and `api` as described in section 5.
See [README.md](README.md) for current behaviour and [API.md](API.md) for the endpoints.*
