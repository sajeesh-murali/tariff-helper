'use strict';
/*
 * Connects the Lansi intake flow to the Tariff Helper backend (same origin, so no CORS):
 *   GET  /api/agreements        agreements and job categories for the form
 *   POST /api/payslips/parse    read a payslip PDF into the form (the person checks the values)
 *   POST /api/estimates         the estimate; the code calculates, the AI only explains
 *
 * Uses globals from index.html: intake, state, go, render, esc, money, title, intakeProgress, intakeError,
 * historyTopics, CUR_YM. Nothing is stored in the browser.
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
  intake.q = 0;
  go(2);
}

/* ---------- step 2 -> 3: build the estimate request from the answers ---------- */

function monthsBetween(from, to) {
  const [fy, fm] = from.split('-').map(Number);
  const [ty, tm] = to.split('-').map(Number);
  return (ty - fy) * 12 + (tm - fm) + 1;
}

function toDuration(months) {
  const capped = Math.min(months, 60 * 12 + 11);
  return { years: Math.floor(capped / 12), months: capped % 12 };
}

/** Total months of a history topic, whether any period was approximate, and the overlap answers given. */
function topicTotal(key) {
  const answer = intake.answers[key];
  const total = { months: 0, approx: false, overlaps: [], answeredYes: false };
  if (!answer || answer.choice !== 'yes') return total;
  total.answeredYes = true;
  for (const p of answer.periods) {
    let m = 0;
    if (p.approx) {
      const years = Number(p.years);
      if (years > 0) { m = Math.round(years * 12); total.approx = true; }
    } else if (p.start && p.end) {
      m = monthsBetween(p.start, p.end);
    }
    if (m > 0) { total.months += m; total.overlaps.push(p.overlap); }
  }
  return total;
}

/** Returns the request body and the list of answers that are not part of the calculation. */
function buildEstimate() {
  const ag = agreementById(intake.agreementId);
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

  // The current job: dated when the start month is known, otherwise part of "work in Norway".
  const current = intake.current;
  const privateEmployer = ag && String(ag.sector).startsWith('private');
  let norwayMonths = 0;
  let norwayApprox = false;
  if (!current.approx && current.start) {
    body.experiences.push({
      country: 'NO',
      kind: privateEmployer ? 'private_service' : 'public_service',
      startDate: current.start,
      endDate: CUR_YM,
      positionPercent: pct
    });
  } else if (current.approx && Number(current.years) > 0) {
    norwayMonths += Math.round(Number(current.years) * 12);
    norwayApprox = true;
  }

  const norway = topicTotal('norway');
  const enk = topicTotal('enk');
  const abroad = topicTotal('abroad');
  const aupair = topicTotal('aupair');
  const care = topicTotal('care');
  const notCounted = [];
  for (const key of ['leave', 'other']) {
    if (topicTotal(key).answeredYes) notCounted.push(historyTopics.find(t => t[0] === key)[1]);
  }

  const screening = {};
  const workInNorway = norwayMonths + norway.months + enk.months;
  if (workInNorway > 0) {
    screening.workInNorway = {
      duration: toDuration(workInNorway),
      approximate: norwayApprox || norway.approx || enk.approx
    };
    if (enk.months > 0) screening.workInNorway.selfEmployed = toDuration(enk.months);
  }
  if (abroad.months > 0) screening.workAbroad = { duration: toDuration(abroad.months), approximate: abroad.approx };
  if (aupair.months > 0) screening.auPair = { duration: toDuration(aupair.months), approximate: aupair.approx };
  if (care.months > 0) screening.otherHome = { type: 'care_work', duration: toDuration(care.months), approximate: care.approx };

  if (Object.keys(screening).length) {
    const overlaps = [norway, enk, abroad, aupair, care].flatMap(t => t.overlaps);
    if (overlaps.length > 1) {
      screening.periodsOverlap = overlaps.includes('yes') ? 'YES' : overlaps.every(o => o === 'no') ? 'NO' : 'UNSURE';
    }
    body.screening = screening;
  }
  return { body, notCounted };
}

async function submitEstimate() {
  const { body, notCounted } = buildEstimate();
  intake.notCounted = notCounted;
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
  const notCounted = (intake.notCounted || []).length
    ? `<div class="notice">Disse svarene er ikke med i beregningen ennå: ${intake.notCounted.map(t => esc(t)).join(' · ')}</div>` : '';
  return head + assumed
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
    + notCounted
    + `<div class="card"><h2>Forklaring</h2><p class="small">Skrevet av en KI-modell, på engelsk, ut fra tallene over. `
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
