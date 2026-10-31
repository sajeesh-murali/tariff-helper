'use strict';
/*
 * Connects the Lansi intake flow to the Tariff Helper backend (same origin, so no CORS):
 *   GET  /api/agreements        agreements and job categories for the form
 *   POST /api/payslips/parse    read a payslip PDF into the form (the person checks the values)
 *   POST /api/estimates         the estimate; the code calculates, the AI only explains
 *
 * Uses globals from index.html: intake, state, go, render, esc, money, title, intakeProgress, intakeError, invalidateIntake,
 * CUR_YM. Nothing is stored in the browser.
 */

const MAX_PAYSLIP_BYTES = 2 * 1024 * 1024;
const FORM_TEXT = /^[\p{L}\p{N} .,&()/'-]+$/u;

let agreementsApi = [];

function loadAgreements() {
  fetch('/api/agreements')
    .then(r => (r.ok ? r.json() : []))
    .then(list => {
      agreementsApi = Array.isArray(list) ? list : [];
      if (intake.started && state.step === 1) render();
    })
    .catch(() => { agreementsApi = []; });
}

function agreementById(id) {
  return agreementsApi.find(a => a.id === id) || null;
}

/* ---------- small helpers ---------- */

function setBusy(text) {
  clearBusy();
  const d = document.createElement('div');
  d.id = 'busy';
  d.setAttribute('role', 'status');
  d.setAttribute('aria-live', 'polite');
  d.style.cssText = 'position:fixed;inset:0;z-index:50;display:flex;align-items:center;justify-content:center;'
    + 'background:rgba(246,248,246,.88);font-size:20px;color:var(--ink);text-align:center;padding:24px';
  d.textContent = text;
  document.body.appendChild(d);
}

function clearBusy() {
  document.getElementById('busy')?.remove();
}

async function callApi(url, options) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 90000);
  try {
    const res = await fetch(url, { ...options, signal: controller.signal });
    let data = null;
    try { data = await res.json(); } catch (e) { data = null; }
    return { ok: res.ok, status: res.status, data };
  } catch (e) {
    return { ok: false, status: 0, data: null };
  } finally {
    clearTimeout(timer);
  }
}

function errorText(res) {
  if (res.status === 0) return 'Vi fikk ikke kontakt med tjenesten. Sjekk nettforbindelsen og prøv igjen.';
  if (res.status === 429) return 'For mange forespørsler akkurat nå. Vent et minutt og prøv igjen.';
  if (res.status === 502 || res.status === 503) return 'Tjenesten er midlertidig utilgjengelig. Prøv igjen senere.';
  if (res.status === 400 && res.data) {
    const fields = res.data.errors && typeof res.data.errors === 'object'
      ? Object.entries(res.data.errors).map(([k, v]) => `${k}: ${v}`).join('; ') : '';
    return 'Opplysningene kunne ikke brukes: ' + (fields || res.data.detail || 'ugyldig forespørsel') + '.';
  }
  return 'Noe gikk galt. Prøv igjen senere.';
}

/* ---------- step 1: payslip upload ---------- */

function startIntake(manual = false) {
  intake = freshIntake();
  intake.started = true;
  intake.manual = manual;
  go(1);
}

function pickPayslip() {
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'application/pdf,.pdf';
  input.onchange = () => { if (input.files[0]) uploadPayslip(input.files[0]); };
  input.click();
}

async function uploadPayslip(file) {
  intake = freshIntake();
  intake.started = true;
  if (!/\.pdf$/i.test(file.name) && file.type !== 'application/pdf') {
    intake.manual = true;
    intake.error = 'Lønnsslippen må være en PDF-fil. Du kan også fylle inn selv.';
    go(1);
    return;
  }
  if (file.size > MAX_PAYSLIP_BYTES) {
    intake.manual = true;
    intake.error = 'Filen er for stor (maks 2 MB). Du kan også fylle inn selv.';
    go(1);
    return;
  }
  setBusy('Leser lønnsslippen …');
  const form = new FormData();
  form.append('file', file);
  const res = await callApi('/api/payslips/parse', { method: 'POST', body: form });
  clearBusy();
  if (res.ok && res.data) {
    applyPayslip(res.data);
  } else {
    intake.manual = true;
    intake.error = res.status === 400 && res.data?.detail
      ? 'Vi kunne ikke lese lønnsslippen (' + res.data.detail + '). Fyll inn selv.'
      : errorText(res) + ' Du kan fylle inn selv.';
  }
  go(1);
}

function applyPayslip(p) {
  intake.employer = p.employerName || '';
  intake.job = p.jobTitle || '';
  intake.percent = p.positionPercent ?? 100;
  intake.salary = p.annualSalaryFullTime != null ? String(p.annualSalaryFullTime) : '';
  intake.ansiennitetsdato = p.ansiennitetsdato || '';
  intake.stillingskode = p.stillingskode || '';
  intake.lonnstrinn = p.lonnstrinn != null ? String(p.lonnstrinn) : '';
  if (p.agreement) {
    intake.agreementId = p.agreement.agreementId;
    intake.jobCategory = p.agreement.jobCategory || '';
  }
  intake.fromPayslip = true;
}

/* ---------- step 1: the form the person checks ---------- */

function setAgreement(id) {
  intake.agreementId = id;
  const ag = agreementById(id);
  if (!ag || !ag.jobCategories.some(c => c.id === intake.jobCategory)) intake.jobCategory = '';
  intake.confirmed = false;
  invalidateIntake();
  render();
}

function agreementSelect() {
  const options = agreementsApi.map(a => `<option value="${esc(a.id)}" ${intake.agreementId === a.id ? 'selected' : ''}>`
    + `${esc(a.name)}${a.status === 'placeholder' ? ' (ingen beregning ennå)' : ''}</option>`).join('');
  return `<div><label for="intake-agreement">Tariffavtale</label><select id="intake-agreement" `
    + `onchange="setAgreement(this.value)"><option value="">Jeg er usikker (finn ut fra arbeidsgiver)</option>${options}</select></div>`;
}

function jobCategorySelect() {
  const ag = agreementById(intake.agreementId);
  if (!ag || !ag.jobCategories.length) return '';
  const options = ag.jobCategories.map(c => `<option value="${esc(c.id)}" ${intake.jobCategory === c.id ? 'selected' : ''}>`
    + `${esc(c.label)}</option>`).join('');
  return `<div><label for="intake-jobCategory">Stillingstype</label><select id="intake-jobCategory" `
    + `onchange="intakeField('jobCategory',this.value)"><option value="">Velg stilling</option>${options}</select></div>`;
}

function intakeConfirm() {
  const field = (k, label, type, extra = '', required = false) => `<div><label for="intake-${k}">${label}</label>`
    + `<input id="intake-${k}" type="${type}" value="${esc(intake[k])}" ${extra} ${required ? 'required' : ''} `
    + `oninput="intakeField('${k}',this.value)"></div>`;
  const lead = intake.manual
    ? 'Fyll inn det du vet. Årslønn og stillingsprosent trengs for å regne ut et anslag.'
    : 'Vi har lest disse opplysningene automatisk fra lønnsslippen. Sjekk dem mot lønnsslippen din og rett det som er feil.';
  return intakeProgress(1) + `<section class="intake-work">${title(intake.manual ? 'Fyll inn selv' : 'Fra lønnsslippen din',
    'Stemmer opplysningene?', lead)}<form onsubmit="event.preventDefault();confirmIntake()" class="card">`
    + `<div class="fieldgrid">`
    + field('employer', 'Arbeidsgiver', 'text', 'maxlength="100"', true)
    + field('job', 'Stilling', 'text', 'maxlength="60"', true)
    + field('salary', 'Årslønn i 100 % stilling, før skatt', 'number', 'min="1" max="5000000" step="any"', true)
    + field('percent', 'Stillingsprosent', 'number', 'min="1" max="100" step="1"', true)
    + agreementSelect()
    + jobCategorySelect()
    + field('ansiennitetsdato', 'Ansiennitetsdato på lønnsslippen (valgfritt)', 'month', `min="1950-01" max="${CUR_YM}"`)
    + field('lonnstrinn', 'Lønnstrinn (valgfritt)', 'number', 'min="1" max="100" step="1"')
    + field('stillingskode', 'Stillingskode (valgfritt)', 'text', 'maxlength="6" inputmode="numeric"')
    + `</div><p class="small">Ansiennitetsdato, lønnstrinn og stillingskode brukes bare til å sammenligne med det vi regner ut. `
    + `De sendes aldri til KI. Velg «Jeg er usikker» hvis du ikke vet hvilken avtale som gjelder; da bruker vi arbeidsgivernavnet. `
    + `For private barnehager må du velge avtalen selv.</p>${intakeError()}`
    + `<div class="actions"><button type="submit" class="btn">Bekreft og gå videre</button>`
    + `<button type="button" class="textbtn" onclick="go(0)">Til forsiden</button></div></form></section>`;
}

function confirmIntake() {
  intake.error = '';
  const ag = agreementById(intake.agreementId);
  const pct = Number(intake.percent);
  const salary = Number(intake.salary);
  const sent = (v) => String(v).trim();
  if (ag && ag.status === 'placeholder') {
    intake.error = 'For denne avtalen kan vi ikke gi et anslag ennå. ' + (ag.unavailableReason || '');
  } else if (!ag && !sent(intake.employer)) {
    intake.error = 'Fyll inn arbeidsgiver, eller velg tariffavtale.';
  } else if (!ag && !FORM_TEXT.test(sent(intake.employer))) {
    intake.error = 'Arbeidsgiver kan bare inneholde bokstaver, tall, mellomrom og . , & ( ) / \' -';
  } else if (ag && ag.jobCategories.length && !intake.jobCategory) {
    intake.error = 'Velg stillingstype.';
  } else if ((!ag || !ag.jobCategories.length) && !sent(intake.job)) {
    intake.error = 'Fyll inn stilling.';
  } else if (!intake.jobCategory && sent(intake.job) && !FORM_TEXT.test(sent(intake.job))) {
    intake.error = 'Stilling kan bare inneholde bokstaver, tall, mellomrom og . , & ( ) / \' -';
  } else if (!Number.isInteger(pct) || pct < 1 || pct > 100) {
    intake.error = 'Stillingsprosent må være et heltall mellom 1 og 100.';
  } else if (intake.salary === '' || !Number.isFinite(salary) || salary <= 0 || salary > 5000000) {
    intake.error = 'Oppgi årslønn i 100 % stilling (et positivt beløp).';
  } else if (intake.ansiennitetsdato && !(validMonth(intake.ansiennitetsdato) && intake.ansiennitetsdato >= '1950-01')) {
    intake.error = 'Ansiennitetsdato må være en måned mellom januar 1950 og nå.';
  } else if (intake.lonnstrinn !== '' && !(Number.isInteger(Number(intake.lonnstrinn)) && Number(intake.lonnstrinn) >= 1 && Number(intake.lonnstrinn) <= 100)) {
    intake.error = 'Lønnstrinn må være et heltall mellom 1 og 100.';
  } else if (intake.stillingskode && !/^\d{1,6}$/.test(intake.stillingskode)) {
    intake.error = 'Stillingskode kan bare være opptil 6 siffer.';
  }
  if (intake.error) { render(); return; }
  intake.confirmed = true;
  go(2);
}

/* ---------- step 2: the six screening questions ---------- */

/*
 * The answers are total time without dates (years + months), each with "this is an estimate". They are sent as
 * `screening` to POST /api/estimates, which turns them into periods and applies the agreement's rules.
 * The current job is part of question 1, so no dated periods are sent.
 */

function freshScreen() {
  const time = () => ({ y: '', m: '', approx: false });
  return {
    norway: { ...time(), own: false, ownY: '', ownM: '' },
    abroad: { ...time(), own: false, ownY: '', ownM: '' },
    aupair: { has: '', ...time() },
    military: { has: '', ...time(), country: 'NO' },
    children: { has: '', count: '', ...time() },
    otherHome: { has: '', type: 'care_work', ...time() },
    overlap: ''
  };
}

function S() {
  if (!intake.screen) intake.screen = freshScreen();
  return intake.screen;
}

function scr(key, field, value) {
  invalidateIntake();
  S()[key][field] = value;
}

function scrToggle(key, field, value) {
  scr(key, field, value);
  render();
}

function scrOverlap(value) {
  invalidateIntake();
  S().overlap = value;
}

function timeFields(key, yField = 'y', mField = 'm') {
  const a = S()[key];
  const id = `scr-${key}-${yField}`;
  return `<div class="fieldgrid"><div><label for="${id}">År</label>`
    + `<input id="${id}" type="number" min="0" max="60" step="1" value="${esc(a[yField])}" oninput="scr('${key}','${yField}',this.value)"></div>`
    + `<div><label for="${id}-m">Måneder</label>`
    + `<input id="${id}-m" type="number" min="0" max="11" step="1" value="${esc(a[mField])}" oninput="scr('${key}','${mField}',this.value)"></div></div>`;
}

function approxBox(key) {
  return `<label class="checkline"><input type="checkbox" ${S()[key].approx ? 'checked' : ''} `
    + `onchange="scr('${key}','approx',this.checked)">Dette er et anslag</label>`;
}

function yesNo(key, label) {
  const a = S()[key];
  return `<div class="answer-buttons" role="group" aria-label="${esc(label)}">`
    + [['yes', 'Ja'], ['no', 'Nei']].map(([v, t]) =>
      `<button type="button" aria-pressed="${a.has === v}" onclick="scrToggle('${key}','has','${v}')">${t}</button>`).join('')
    + `</div>`;
}

function ownFirmFields(key) {
  const a = S()[key];
  return `<label class="checkline"><input type="checkbox" ${a.own ? 'checked' : ''} `
    + `onchange="scrToggle('${key}','own',this.checked)">Noe av dette var arbeid i eget firma eller som selvstendig</label>`
    + (a.own ? `<p class="small">Hvor lenge av tiden over var det eget firma? Den erfaringen vurderes separat.</p>${timeFields(key, 'ownY', 'ownM')}` : '');
}

function qCard(n, text, inner) {
  return `<div class="card question-card"><h2>${n}. ${text}</h2>${inner}</div>`;
}

function intakeHistory() {
  const s = S();
  const q1 = qCard(1, 'Hvor lenge har du jobbet i Norge til sammen?',
    `<p class="small">Ta med nåværende jobb, tidligere jobber, deltidsjobber og arbeid i eget firma. Ikke ta med au pair eller verneplikt.</p>`
    + timeFields('norway') + approxBox('norway') + ownFirmFields('norway'));
  const q2 = qCard(2, 'Hvor lenge har du jobbet i andre land enn Norge?',
    `<p class="small">Ikke ta med au pair eller verneplikt.</p>`
    + timeFields('abroad') + approxBox('abroad') + ownFirmFields('abroad'));
  const q3 = qCard(3, 'Har du jobbet som au pair?', yesNo('aupair', 'Har du jobbet som au pair?')
    + (s.aupair.has === 'yes' ? timeFields('aupair') + approxBox('aupair') : ''));
  const q4 = qCard(4, 'Har du avtjent verneplikt i Norge eller et annet land?', yesNo('military', 'Har du avtjent verneplikt?')
    + (s.military.has === 'yes'
      ? timeFields('military')
        + `<label for="scr-military-country">Land</label><select id="scr-military-country" onchange="scr('military','country',this.value)">`
        + `<option value="NO" ${s.military.country === 'NO' ? 'selected' : ''}>Norge</option>`
        + `<option value="" ${s.military.country === '' ? 'selected' : ''}>Annet land</option></select>` + approxBox('military')
      : ''));
  const q5 = qCard(5, 'Har du barn?', yesNo('children', 'Har du barn?')
    + (s.children.has === 'yes'
      ? `<label for="scr-children-count">Hvor mange barn?</label><input id="scr-children-count" type="number" min="1" max="20" step="1" `
        + `value="${esc(s.children.count)}" oninput="scr('children','count',this.value)">`
        + `<p>Hvor lenge har du samlet vært hjemme med omsorg for barn?</p>` + timeFields('children') + approxBox('children')
        + `<p class="small">Antall barn alene gir ikke ansiennitet. Vi trenger hvor lenge du var hjemme med omsorg.</p>`
      : ''));
  const q6 = qCard(6, 'Har du hatt andre perioder med arbeid eller omsorg i hjemmet?',
    `<p class="small">Ikke ta med tiden du allerede har oppgitt for omsorg for barn.</p>` + yesNo('otherHome', 'Andre perioder i hjemmet?')
    + (s.otherHome.has === 'yes'
      ? `<label for="scr-other-type">Hva slags periode?</label><select id="scr-other-type" onchange="scr('otherHome','type',this.value)">`
        + `<option value="care_work" ${s.otherHome.type === 'care_work' ? 'selected' : ''}>Omsorg (barn, eldre eller syke)</option>`
        + `<option value="home_work" ${s.otherHome.type === 'home_work' ? 'selected' : ''}>Arbeid i hjemmet</option></select>`
        + timeFields('otherHome') + approxBox('otherHome')
      : ''));
  const overlap = `<div class="card question-card"><h2>Har noen av periodene du har oppgitt vært samtidig?</h2>`
    + `<p class="small">Ellers risikerer vi å summere jobb, au pair og omsorg for samme tidsrom.</p>`
    + `<select aria-label="Samtidige perioder" onchange="scrOverlap(this.value)">`
    + [['', 'Velg'], ['no', 'Nei'], ['yes', 'Ja'], ['unsure', 'Usikker']].map(([v, t]) =>
      `<option value="${v}" ${s.overlap === v ? 'selected' : ''}>${t}</option>`).join('') + `</select></div>`;
  return intakeProgress(2) + `<section class="intake-work">${title('Arbeid og erfaring', 'Hvilken erfaring har du?',
    'Oppgi omtrent hvor lenge hver erfaring varte. Deltidsarbeid oppgir du som tiden du var ansatt, ikke omregnet til heltidsarbeid. '
    + 'Har du hatt flere jobber samtidig, teller du perioden én gang. Du trenger ikke finne frem attester nå.')}`
    + q1 + q2 + q3 + q4 + q5 + q6 + overlap + intakeError()
    + `<div class="actions"><button class="btn" onclick="submitScreening()">Se resultatet</button>`
    + `<button class="textbtn" onclick="go(1)">Tilbake</button></div></section>`;
}

function timeValue(a, yField = 'y', mField = 'm') {
  const y = a[yField] === '' ? 0 : Number(a[yField]);
  const m = a[mField] === '' ? 0 : Number(a[mField]);
  if (!Number.isInteger(y) || y < 0 || y > 60 || !Number.isInteger(m) || m < 0 || m > 11) return null;
  return { years: y, months: m, total: y * 12 + m };
}

/** Returns an error text in Norwegian, or '' when the answers can be sent. */
function screeningError() {
  const s = S();
  for (const [key, n] of [['norway', 1], ['abroad', 2]]) {
    const t = timeValue(s[key]);
    if (!t) return `Spørsmål ${n}: oppgi år (0–60) og måneder (0–11) som hele tall.`;
    if (s[key].own) {
      const own = timeValue(s[key], 'ownY', 'ownM');
      if (!own || own.total === 0) return `Spørsmål ${n}: oppgi hvor lenge det var eget firma.`;
      if (own.total > t.total) return `Spørsmål ${n}: tiden i eget firma kan ikke være lengre enn den totale tiden.`;
    }
  }
  for (const [key, n] of [['aupair', 3], ['military', 4], ['children', 5], ['otherHome', 6]]) {
    const a = s[key];
    if (a.has === '') return `Spørsmål ${n}: svar ja eller nei.`;
    if (a.has === 'yes') {
      const t = timeValue(a);
      if (!t) return `Spørsmål ${n}: oppgi år (0–60) og måneder (0–11) som hele tall.`;
      if (t.total === 0) return `Spørsmål ${n}: oppgi hvor lenge, eller svar nei.`;
    }
  }
  if (s.children.has === 'yes') {
    const c = Number(s.children.count);
    if (!Number.isInteger(c) || c < 1 || c > 20) return 'Spørsmål 5: oppgi antall barn (1–20).';
  }
  return '';
}

function submitScreening() {
  intake.error = screeningError();
  if (intake.error) { render(); return; }
  submitEstimate();
}

/** The request body for POST /api/estimates. Payslip values are sent only as the estimate API accepts them. */
function buildEstimate() {
  const s = S();
  const pct = Number(intake.percent);
  const body = {
    positionPercent: pct,
    currentAnnualSalary: Math.round(Number(intake.salary) * pct / 100),
    experiences: []
  };
  if (intake.agreementId) {
    body.agreementId = intake.agreementId;
  } else {
    body.employerName = intake.employer.trim();
  }
  if (intake.jobCategory) {
    body.jobCategory = intake.jobCategory;
  } else if (intake.job.trim()) {
    body.jobTitle = intake.job.trim();
  }
  if (intake.stillingskode) body.stillingskode = intake.stillingskode;
  if (intake.lonnstrinn !== '') body.lonnstrinn = Number(intake.lonnstrinn);
  if (intake.ansiennitetsdato) body.ansiennitetsdato = intake.ansiennitetsdato;

  const dur = t => ({ years: t.years, months: t.months });
  const screening = {};
  for (const [key, field] of [['norway', 'workInNorway'], ['abroad', 'workAbroad']]) {
    const t = timeValue(s[key]);
    if (t.total > 0) {
      screening[field] = { duration: dur(t), approximate: s[key].approx };
      if (s[key].own) screening[field].selfEmployed = dur(timeValue(s[key], 'ownY', 'ownM'));
    }
  }
  if (s.aupair.has === 'yes') {
    screening.auPair = { duration: dur(timeValue(s.aupair)), approximate: s.aupair.approx };
  }
  if (s.military.has === 'yes') {
    screening.militaryService = { duration: dur(timeValue(s.military)), approximate: s.military.approx };
    if (s.military.country) screening.militaryService.country = s.military.country;
  }
  if (s.children.has === 'yes') {
    screening.childCare = { children: Number(s.children.count), duration: dur(timeValue(s.children)),
      approximate: s.children.approx };
  }
  if (s.otherHome.has === 'yes') {
    screening.otherHome = { type: s.otherHome.type, duration: dur(timeValue(s.otherHome)),
      approximate: s.otherHome.approx };
  }
  if (s.overlap) screening.periodsOverlap = s.overlap.toUpperCase();
  if (Object.keys(screening).length) body.screening = screening;
  return body;
}

async function submitEstimate() {
  const body = buildEstimate();
  setBusy('Beregner anslaget …');
  const res = await callApi('/api/estimates', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  clearBusy();
  if (res.ok && res.data) {
    intake.result = toResult(res.data);
  } else if (res.status === 422 && res.data?.availableAgreements) {
    intake.result = { kind: 'unavailable', monthly: 0,
      message: 'Vi klarte ikke å finne riktig tariffavtale ut fra arbeidsgiveren. Gå tilbake og velg avtalen selv.' };
  } else if (res.status === 422 && res.data) {
    intake.result = { kind: 'unavailable', monthly: 0, message: res.data.detail || 'Ingen beregning for denne avtalen ennå.' };
  } else {
    intake.result = { kind: 'error', monthly: 0, message: errorText(res) };
  }
  go(3);
}

function toResult(data) {
  const pct = Number(intake.percent) / 100;
  const min = Number(data.differenceFromCurrent.min);
  const max = Number(data.differenceFromCurrent.max);
  const kind = min > 0 ? 'potential' : max > 0 ? 'possible' : 'none';
  return { kind, monthly: kind === 'potential' ? min * pct / 12 : 0, api: data };
}

/* ---------- step 3: the result ---------- */

const LEVELS = { high: 'høy', medium: 'middels', low: 'lav' };

function fmtYears(n) {
  return Number(n).toLocaleString('nb-NO', { maximumFractionDigits: 2 }) + ' år';
}

function fmtRange(r) {
  const min = Number(r.min);
  const max = Number(r.max);
  return min === max ? money(min) : money(min) + ' – ' + money(max);
}

function paragraphs(text) {
  return String(text || '').split(/\n\s*\n/).map(t => `<p>${esc(t.trim())}</p>`).join('');
}

// Decorative: the text next to it carries the meaning, so colour is never the only signal.
// The colour follows the monthly increase shown on the page (before tax, at the person's position percentage).
const STATUS_ICONS = {
  green: '<path class="mark" d="M22 42q14 14 28 0"/>',
  lightgreen: '<path class="mark" d="M24 43q12 7 24 0"/>',
  yellow: '<path class="mark" d="M24 44h24"/>'
};

function statusTier(monthly) {
  return monthly > 2000 ? 'green' : monthly >= 1000 ? 'lightgreen' : 'yellow';
}

function statusIcon(monthly) {
  const tier = statusTier(monthly);
  return `<svg class="status-icon ${tier}" viewBox="0 0 72 72" aria-hidden="true" focusable="false">`
    + `<circle class="face" cx="36" cy="36" r="31"/><circle class="dot" cx="26" cy="29" r="3.5"/>`
    + `<circle class="dot" cx="46" cy="29" r="3.5"/>${STATUS_ICONS[tier]}</svg>`;
}

function resultCard(r) {
  const d = r.api;
  const pct = Number(intake.percent) / 100;
  const diffMin = Number(d.differenceFromCurrent.min);
  const diffMax = Number(d.differenceFromCurrent.max);
  let head;
  if (r.kind === 'potential') {
    head = `<span class="badge">Mulighet for høyere lønn</span><h1>Minstelønnen etter reglene<br>er høyere enn lønnen din.</h1>`
      + `<p class="lead">Ut fra opplysningene dine kan du ha rett til høyere lønn. Du velger selv om du vil ha hjelp til å få saken vurdert.</p>`
      + `<div class="result-amount"><strong>${money(diffMin * pct / 12)}</strong><span>mulig økning per måned før skatt (minst)</span>`
      + `<small>${money(diffMin * pct)} per år · ${esc(intake.percent)} % stilling`
      + (diffMax > diffMin ? ` · opptil ${money(diffMax * pct / 12)} per måned hvis alt usikkert godtas` : '') + `</small></div>`;
  } else if (r.kind === 'possible') {
    head = `<span class="badge amber">Kan være mulig</span><h1>Det kan være mulig<br>å få høyere lønn.</h1>`
      + `<p class="lead">Det avhenger av om arbeidsgiveren godtar perioder som er usikre.</p>`
      + `<div class="result-amount"><strong>${money(diffMax * pct / 12)}</strong><span>mulig økning per måned før skatt, hvis alt usikkert godtas</span>`
      + `<small>${money(diffMax * pct)} per år · ${esc(intake.percent)} % stilling</small></div>`;
  } else {
    head = `<span class="badge">Ingen økning i minstelønn</span><h1>Lønnen din ligger på eller<br>over minstelønnen.</h1>`
      + `<p class="lead">Ut fra opplysningene dine gir avtalen ikke høyere minstelønn enn du har. Arbeidsgiveren kan likevel ha regnet ansienniteten annerledes.</p>`;
  }
  const conf = d.confidence || { level: 'low', reasons: [] };
  const credited = d.creditedVsCounted
    ? `<div class="paper-row"><span>Arbeidsgiveren regner med (fra ${d.creditedVsCounted.basis === 'lonnstrinn' ? 'lønnstrinn' : 'ansiennitetsdato'})</span>`
      + `<b>${fmtYears(d.creditedVsCounted.creditedYears)}</b></div>` : '';
  const assumed = d.agreementSuggestion?.assumed
    ? `<div class="notice">Avtalen er antatt ut fra arbeidsgivernavnet. Kontroller at den stemmer.</div>` : '';
  const allYears = Number(d.countedYearsIfAllAccepted) > Number(d.countedYears)
    ? ` (opptil ${fmtYears(d.countedYearsIfAllAccepted)} hvis alt usikkert godtas)` : '';
  const monthly = r.kind === 'potential' ? diffMin * pct / 12 : r.kind === 'possible' ? diffMax * pct / 12 : 0;
  return statusIcon(monthly) + head + assumed
    + `<div class="card"><h2>Slik kom vi frem til anslaget</h2>`
    + `<div class="paper-row"><span>Tariffavtale</span><b>${esc(d.agreementApplied)} (satser fra ${esc(d.rulesValidFrom)})</b></div>`
    + `<div class="paper-row"><span>Stilling</span><b>${esc(d.jobCategory)}</b></div>`
    + `<div class="paper-row"><span>Ansiennitet som teller</span><b>${fmtYears(d.countedYears)}${allYears}</b></div>`
    + credited
    + `<div class="paper-row"><span>Minstelønn ved denne ansienniteten (100 % stilling)</span><b>${fmtRange(d.estimatedMinimumSalary)}</b></div>`
    + `<div class="paper-row"><span>Lønnen din omregnet til 100 % stilling</span><b>${money(d.currentFullTimeSalary)}</b></div>`
    + `<div class="paper-row"><span>Forskjell per år (100 % stilling)</span><b>${fmtRange(d.differenceFromCurrent)}</b></div>`
    + `<div class="paper-row"><span>Hvor sikkert er anslaget?</span><b>${esc(LEVELS[conf.level] || conf.level)}</b></div>`
    + `<ul class="small">${(conf.reasons || []).map(x => `<li>${esc(x)}</li>`).join('')}</ul></div>`
    + `<div class="card"><h2>Forklaring</h2><p class="small">Skrevet av en KI-modell, på norsk, ut fra tallene over. `
    + `Tallene er regnet ut av programkode, ikke av KI.</p>${paragraphs(d.explanation)}</div>`
    + `<div class="card"><h2>Forbehold</h2><ul class="small">${(d.caveats || []).map(x => `<li>${esc(x)}</li>`).join('')}</ul>`
    + `<p class="small">${esc(d.disclaimer)}</p></div>`
    + (r.kind === 'potential' ? `<button class="btn" onclick="go(4)">Se hvordan vi kan hjelpe</button>` : '');
}

function intakeResult() {
  const r = intake.result;
  let content;
  if (r.api) {
    content = resultCard(r);
  } else if (r.kind === 'unavailable') {
    content = `<span class="badge amber">Ingen beregning ennå</span><h1>Vi kan ikke gi et anslag<br>ennå.</h1>`
      + `<p class="lead">${esc(r.message)}</p><button class="btn" onclick="go(1)">Se over opplysningene</button>`;
  } else {
    content = `<span class="badge amber">Noe gikk galt</span><h1>Vi fikk ikke laget<br>et anslag.</h1>`
      + `<p class="lead">${esc(r.message)}</p><button class="btn" onclick="go(2)">Prøv igjen</button>`;
  }
  return intakeProgress(3) + `<section class="intake-work result-page">${content}<div class="actions">`
    + `<button class="textbtn" onclick="go(2)">Endre svarene mine</button>`
    + `<button class="textbtn" onclick="go(0)">Til forsiden</button></div></section>`;
}

document.addEventListener('DOMContentLoaded', loadAgreements);
