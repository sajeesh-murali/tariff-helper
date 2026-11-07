package com.tariffhelper.service;

import com.tariffhelper.api.FormText;
import com.tariffhelper.domain.PayslipFields;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the form values out of the text of a payslip with fixed rules, before any model is asked. Payslips have no
 * common layout, so it works from what they have in common:
 * <ul>
 *   <li>a dictionary of titles and abbreviations (Stillingsprosent, St-%, St.kode, Ltr, Ans.dato, ...), matched without
 *   regard to case, accents, punctuation or a one-letter text recognition mistake;</li>
 *   <li>a title followed by its value on the same line ("Lønnstrinn 34");</li>
 *   <li>a row of column titles with the values on the row below, paired by their position on the line (the picture
 *   reader keeps the gaps between columns);</li>
 *   <li>a row that holds a code, a title, a percentage and a date with no column titles at all.</li>
 * </ul>
 * Every value is copied from the text and checked (range, character whitelist), so nothing is made up. Nothing here
 * leaves the server, so the whole text can be used, unlike what is sent to a model.
 *
 * Privacy: nothing is logged.
 */
@Component
public class PayslipRuleReader {

    /** Why a value is empty: the payslip says so, or it simply was not found. */
    public static final String NOT_PRINTED = "notPrinted";

    /**
     * @param notPrinted fields that the payslip itself leaves out or marks as not given (for example 0 %)
     * @param usedLines  the numbers of the lines the values were read from, so the excerpt for the model can include them
     */
    public record Reading(PayslipFields fields, Map<String, String> notPrinted, java.util.Set<Integer> usedLines) {
    }

    /**
     * SALARY_PLAIN is a plain "Årslønn": on some payslips it is the full-time salary, on others the person's own. It is
     * only accepted after a check (see {@link #plainSalary}). RAMMETRINN is one title for pay frame and step ("LR/Ltr
     * 26/45"); the step is the second number.
     */
    enum Field { EMPLOYER, TITLE, PERCENT, SALARY, SALARY_PLAIN, TRINN, RAMMETRINN, KODE, DATE }

    private static final Map<String, Field> LABELS = new HashMap<>();
    /**
     * Titles of other columns. They only mark where a value ends, or keep a number or date that belongs to something
     * else (tax table, pay-line amount, start date, service-time date) from being taken for one of our values.
     */
    private static final java.util.Set<String> STOP_LABELS = new java.util.HashSet<>(List.of(
            "organisasjonsnr", "orgnr", "orgenhet", "orgenhetsnr", "ansattnr", "ansattnummer", "ressursnummer",
            "avtaleomrade", "periode", "periodenr", "utbetalt", "utbetalingsdato", "fodselsnr", "fodselsnummer",
            "fodselsdato", "konto", "lonnskonto", "adresse", "skattekort", "lonnsramme", "lramme",
            "lr", "alt", "navn", "arbeidssted", "utlonningssted", "lonnsmottaker", "juridiskorganisasjonsnr",
            "arbeidsgivernr", "tab", "kode", "lonntrekk", "betegnelse2", "stillingsprosent2",
            // tax and holiday pay
            "skattetrekk", "forskuddstrekk", "trekkgrunnlag", "skattegrunnlag", "skattetabell", "tabell",
            "tabellnr", "tabelltrekk", "trekkprosent", "skatteprosent", "prosenttrekk", "skattekommune",
            "bostedskommune", "frikort", "feriepenger", "feriepengegrunnlag", "opptjentferiepenger", "ferietrekk",
            // totals and bank
            "bruttolonn", "sumbrutto", "nettolonn", "nettoutbetalt", "utbetales", "tilutbetaling",
            "hittiliar", "hittil", "saldo", "kontonr", "kontonummer", "bankkonto", "iban",
            // the pay-line table (Lønnsart, Tekst/Betegnelse, Antall, Sats, Beløp)
            "lonnsart", "lonnart", "art", "tekst", "beskrivelse", "antall", "sats", "belop", "grunnlag",
            "antallgrunnlag", "antgrlag", "enhet", "timer",
            // dates that are not the seniority date
            "fradato", "tildato", "fom", "tom", "gyldigfra", "gyldigtil", "lonnsperiode", "opptjeningsperiode",
            "opptjent", "bilagsdato", "lonnsdato", "kjorenr", "ansettelsesdato", "ansattdato", "ansattfra",
            "tiltredelsesdato", "tiltradt", "startdato", "sluttdato", "endringsdato", "sistelonnsendring",
            "lonnsendringsdato", "nesteopprykk", "opprykksdato", "nestelonnsopprykk", "fodt",
            // service-time seniority: not the date the pay seniority counts from
            "ansiennitetsdatoikommunen", "tjenesteansiennitet", "kommuneansiennitet", "annenansiennitetsdato",
            "othersenioritydate",
            // person and organisation
            "personnr", "personnummer", "dnummer", "fnr", "ansnr", "ansnummer", "ansattid", "lonnsmottakernr",
            "postnr", "poststed", "telefon", "epost", "koststed", "kostnadssted", "ansvar", "ansvarssted",
            "avdeling", "avdnr", "prosjekt", "firmanr", "virksomhetsnr", "bedriftsnr", "foretaksnr", "underenhet",
            "orgnummer", "oppdragsgiver", "kunde",
            // position data that is not one of our values (position ID, 7-digit occupation code, pay group, ...)
            "stillingsnr", "stillingsnummer", "stillingsid", "stillingstype", "ansettelsesform", "ansettelsestype",
            "arbeidsforhold", "arbeidsforholdsid", "arbeidsforholdsnr", "arbeidstidsordning", "timerperuke",
            "arbeidstid", "yrkeskode", "styrk", "stillingsgruppe", "sgs", "tariffomrade", "tariffavtale",
            "overenskomst", "lonnsplan", "lonnsspenn", "ramme", "alternativ", "lonnsalternativ", "lonnsklasse",
            "stillingsniva",
            // pay amounts that are not the full-time annual salary
            "arslonnistilling", "basislonn", "manedslonn", "fastlonn", "timelonn", "timesats", "tillegg",
            "fastetillegg", "lonnprmnd",
            // pension and union
            "pensjon", "pensjonstrekk", "pensjonsgrunnlag", "klp", "spk", "fagforening", "fagforeningstrekk"));

    /** A title that holds a code and a title in one ("Stillingskode/tittel 7174 Sykepleier"). */
    private static final java.util.Set<String> CODE_AND_TITLE_LABELS = java.util.Set.of("stillingskodetittel",
            "stillingskodebetegnelse", "stillingskodeogtittel", "stkodetittel", "stillingsgruppebetegnelse");
    /** Short or general titles: a clearer title elsewhere on the slip wins over these. */
    private static final java.util.Set<String> WEAK_LABELS = java.util.Set.of("stilling", "trinn", "ans", "ltr",
            "virksomhet", "firma", "sko");
    /** Position percentage written as a fraction of a full position (0,5 means 50 %). */
    private static final java.util.Set<String> FRACTION_LABELS = java.util.Set.of("stillingsbrok", "stillingsandel");
    /** Words that head the pay-line table; two of them on a line mean the rows below are pay lines, not a position. */
    private static final java.util.Set<String> PAY_TABLE_TITLES = java.util.Set.of("lonnsart", "lonnart", "art",
            "tekst", "antall", "sats", "belop", "grunnlag");
    /** A job title never starts with one of these: it is a pay line (Månedslønn, Kveldstillegg, Skattetrekk). */
    private static final Pattern PAY_LINE_WORD = Pattern.compile(
            "(?:manedslonn|fastlonn|timelonn|lonn|overtid|feriepenger|brutto|netto|skatt\\w*|\\w*tillegg|\\w*trekk)");
    /** Names of payroll systems and bureaus: a letterhead with one of these is not the employer. */
    private static final List<String> PAYROLL_PROVIDERS = List.of("regnskap", "lonnsservice", "lonnssenter",
            "lonnskontor", "visma", "tripletex", "unit4", "agresso", "xledger", "hogia", "huldt", "cegal",
            "poweroffice", "fiken", "mingat", "direktoratetforforvaltning");
    private static final java.util.Set<String> PAYROLL_PROVIDER_WORDS = java.util.Set.of("dfo", "sap");

    static {
        add(Field.EMPLOYER, "arbeidsgiver", "arbeidsgivernavn", "arbeidsgiversnavn", "virksomhet", "arbeidsgiverennavn",
                "arbeidsgjevar", "arbeidsgjevarnamn", "firmanavn", "firma", "bedriftsnavn", "virksomhetsnavn",
                "foretaksnavn", "employer", "employername");
        add(Field.TITLE, "stilling", "stillingstittel", "stillingsbetegnelse", "betegnelse", "tittel", "yrke",
                "stillingstitel", "stillingsnavn", "stillingsbenevnelse", "stillingsnemning", "stillingsnamn",
                "yrkestittel", "yrkesbetegnelse", "jobtitle", "positiontitle");
        add(Field.PERCENT, "stillingsprosent", "stillingsandel", "stillingsbrok", "st%", "stilling%", "stprosent",
                "sysselsettingsgrad", "stillingprosent", "stillingsprosenten", "stillingsstorrelse", "stillings%",
                "still%", "stpst", "stillpst", "stillingspst", "stillingsstorleik", "stillingsandel%",
                "stillingsandeli%", "avtaltstillingsprosent", "faststillingsprosent", "deltidsprosent", "dell%",
                "positionpercentage", "position%", "employmentpercentage", "stillingsdel");
        add(Field.SALARY, "arslonn100%", "arslonni100%stilling", "arslonni100%", "arslonnfulltid", "heltidsarslonn",
                "arslonnheltid", "arslonn100prosent", "arslonni100prosentstilling", "arslonnfor100%",
                "arslonnved100%", "arslonnv100%", "arslonnfullstilling", "arslonnifullstilling", "arslonnhelstilling",
                "arslon100%", "arsloni100%stilling", "arslonheiltid", "annualsalary100%", "fulltimeannualsalary");
        add(Field.SALARY_PLAIN, "arslonn", "arslon", "bruttoarslonn", "fastarslonn", "avtaltarslonn", "annualsalary",
                "grossannualsalary");
        add(Field.TRINN, "lonnstrinn", "ltrinn", "ltr", "lonnstr", "trinn", "lonnstrinnet", "salarygrade", "paygrade",
                "salarystep", "paystep");
        add(Field.RAMMETRINN, "lrltr", "lonnsrammetrinn", "rammetrinn", "lrammeltr");
        add(Field.KODE, "stillingskode", "stkode", "stillkode", "stillingskd", "stillingskoden", "stkd", "sko",
                "stillingskodesko", "stillingskodenr", "stkodenr", "positioncode");
        add(Field.DATE, "ansiennitet", "ansiennitetsdato", "ansdato", "ansiennitetfra", "registrertansiennitet",
                "ansiennitetsdatoen", "ans", "ansiennitetsdatopalonnsslippen", "ansienn", "lonnsansiennitet",
                "lonnsansiennitetsdato", "lonnsans", "lonsansiennitet", "ansiennitetsdatostilling",
                "stillingsansiennitet", "ansiennitetfradato", "ansfra", "ansiennitetfom", "ansfom", "senioritydate",
                "senioritydateforposition", "seniorityfrom");
        for (String key : CODE_AND_TITLE_LABELS) {
            LABELS.putIfAbsent(key, Field.TITLE);
        }
    }

    private static void add(Field field, String... keys) {
        for (String key : keys) {
            LABELS.put(key, field);
        }
    }

    private static final int MAX_WINDOW = 6;
    private static final int ALIGN_TOLERANCE = 4;
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern FORM_TEXT = Pattern.compile(FormText.REGEX);
    private static final Pattern TOKEN = Pattern.compile("\\S+");
    private static final Pattern GAP = Pattern.compile("\\s{3,}");
    private static final Pattern DATE = Pattern.compile(
            "(?<!\\d)(?:(\\d{1,2})[./-])?(\\d{1,2})[./-](\\d{4})(?!\\d)|(?<!\\d)(\\d{4})[-./](\\d{1,2})(?:[-./]\\d{1,2})?(?!\\d)");
    private static final Pattern PERCENT = Pattern.compile("^(\\d{1,3})(?:[.,](\\d{1,2}))?%?$");
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d,])(\\d{1,3}(?:[ .\\u00a0]\\d{3})+|\\d+)(?:,\\d{1,2})?(?!\\d)");
    private static final Pattern CODE_AND_TITLE = Pattern.compile("^\\s*[A-Za-z]?(\\d{4,7})\\s*(?:[-–:]\\s*)?(\\p{L}.*)$");
    private static final Pattern EMPLOYER_NAME = Pattern.compile(
            "\\b(?:kommunen?|fylkeskommunen?|helseforetak(?:et)?|sykehus(?:et)?|hf|direktorat(?:et)?|universit(?:et(?:et)?|y)"
                    + "|h[øo][gy]skolen?|statens|stiftelsen?)\\b", FLAGS);
    /** A company form on one of the first lines ("Eksempel Barnehage AS"). */
    private static final Pattern COMPANY_FORM = Pattern.compile("\\b(?:as|sa)\\b", FLAGS);
    private static final List<String> FUNCTION_WORDS = List.of("og", "i", "for", "pa", "som", "fra", "til", "av", "er",
            "med", "pr", "per");
    private static final List<String> NOT_A_NAME = List.of("andel", "avgift", "nr", "navn", "prosent", "dato", "kode");
    private static final long MAX_SALARY = 5_000_000L;
    private static final YearMonth EARLIEST = YearMonth.of(1950, 1);

    private record Token(String text, int start, int end, String key) {
    }

    private record Hit(Field field, int first, int last, int strength) {
    }

    private record Candidate(String value, int strength, int line) {
    }

    private record Line(String raw, List<Token> tokens, List<Hit> hits) {
    }

    public Reading read(String text) {
        if (text == null || text.isBlank()) {
            return new Reading(PayslipFields.empty(), Map.of(), java.util.Set.of());
        }
        List<Line> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            List<Token> tokens = tokens(raw);
            lines.add(new Line(raw, tokens, spot(tokens)));
        }
        Map<Field, List<Candidate>> found = new EnumMap<>(Field.class);
        Map<String, String> notPrinted = new HashMap<>();
        java.util.Set<Integer> used = new java.util.TreeSet<>();
        java.util.Set<Field> unclear = java.util.EnumSet.noneOf(Field.class);
        for (int i = 0; i < lines.size(); i++) {
            for (Hit hit : lines.get(i).hits()) {
                if (hit.field() != null) {
                    collect(lines, i, hit, found, notPrinted, used, unclear);
                }
            }
        }
        // A slip with a main position and a side position (or two main ones): only the main position's block is read.
        List<Marker> markers = markers(lines);
        for (Map.Entry<Field, List<Candidate>> entry : found.entrySet()) {
            if (entry.getKey() != Field.EMPLOYER) {
                entry.setValue(pick(entry.getValue(), Candidate::line, markers));
            }
        }
        String code = best(found, Field.KODE);
        String title = best(found, Field.TITLE);
        String percent = best(found, Field.PERCENT);
        String date = best(found, Field.DATE);
        List<String[]> shapes = pick(shapeRows(lines), row -> Integer.parseInt(row[4]), markers);
        if (shapes.size() > 1 && shapes.stream().map(row -> row[0]).distinct().count() > 1) {
            // Two rows of the same kind with different codes and no sign of which is the main position: do not guess.
            unclear.addAll(List.of(Field.KODE, Field.TITLE, Field.PERCENT, Field.DATE));
        } else if (!shapes.isEmpty()) {
            String[] shape = shapes.get(0);
            used.add(Integer.parseInt(shape[4]));
            code = code == null ? shape[0] : code;
            title = title == null ? shape[1] : title;
            percent = percent == null ? shape[2] : percent;
            date = date == null ? shape[3] : date;
        }
        if (unclear.contains(Field.KODE)) {
            code = null;
        }
        if (unclear.contains(Field.TITLE)) {
            title = null;
        }
        if (unclear.contains(Field.PERCENT)) {
            percent = null;
        }
        if (unclear.contains(Field.DATE)) {
            date = null;
        }
        String trinn = unclear.contains(Field.TRINN) ? null : best(found, Field.TRINN);
        String salary = unclear.contains(Field.SALARY) ? null : best(found, Field.SALARY);
        if (salary == null && !unclear.contains(Field.SALARY_PLAIN)) {
            salary = plainSalary(best(found, Field.SALARY_PLAIN), percent, lines);
        }
        String employer = best(found, Field.EMPLOYER);
        if (employer == null) {
            employer = letterhead(lines, used);
        }
        PayslipFields fields = new PayslipFields(code, intOrNull(trinn), yearMonth(date),
                employer, title, intOrNull(percent), amountOrNull(salary));
        if (fields.positionPercent() != null) {
            notPrinted.remove("positionPercent");
        }
        if (fields.annualSalaryFullTime() == null && hourlyPay(text)) {
            notPrinted.put("annualSalaryFullTime", NOT_PRINTED);
        }
        if (fields.positionPercent() == null && hourlyPay(text)) {
            notPrinted.put("positionPercent", NOT_PRINTED);
        }
        return new Reading(fields, notPrinted, used);
    }

    /** Hourly pay: there is no yearly salary or position percentage to find. */
    private static boolean hourlyPay(String text) {
        String plain = plain(text);
        return plain.contains("timelonn") && !plain.contains("manedslonn") && !plain.contains("fastlonn");
    }

    // ---- finding the title and its value ----

    /** Returned by {@link #alignedBelow} when the rows under a title disagree and nothing says which one counts. */
    private static final String UNCLEAR = "\u0000unclear";

    private void collect(List<Line> lines, int index, Hit hit, Map<Field, List<Candidate>> found,
                         Map<String, String> notPrinted, java.util.Set<Integer> used, java.util.Set<Field> unclear) {
        Line line = lines.get(index);
        Field target = hit.field() == Field.RAMMETRINN ? Field.TRINN : hit.field();
        int next = nextHitStart(line, hit);
        List<Token> region = line.tokens().subList(hit.last() + 1, next);
        String value = fromRegion(hit.field(), region, isFraction(line, hit));
        if (value == null && skipFiller(region).isEmpty()) {
            value = alignedBelow(lines, index, hit, used);
        }
        if (UNCLEAR.equals(value)) {
            unclear.add(target);
            return;
        }
        if (value == null) {
            return;
        }
        if (hit.field() == Field.PERCENT && "0".equals(value)) {
            notPrinted.put("positionPercent", NOT_PRINTED);
            return;
        }
        if (hit.field() == Field.TITLE) {
            Matcher combined = CODE_AND_TITLE.matcher(value);
            if (combined.matches()) {
                String code = kode(combined.group(1));
                if (code != null) {
                    found.computeIfAbsent(Field.KODE, k -> new ArrayList<>()).add(new Candidate(code, 1, index));
                }
                value = combined.group(2);
            }
            value = validText(value, Limits.JOB_TITLE, true);
        } else if (hit.field() == Field.EMPLOYER) {
            value = validText(value, Limits.EMPLOYER, false);
        } else if (hit.field() == Field.KODE) {
            titleAfterCode(skipFiller(region), index, found);
        }
        if (value != null) {
            found.computeIfAbsent(target, k -> new ArrayList<>()).add(new Candidate(value, hit.strength(), index));
            used.add(index);
        }
    }

    /** "SKO 1364 Seniorrådgiver": the words after the code on the same line are a weak suggestion for the job title. */
    private static void titleAfterCode(List<Token> tokens, int index, Map<Field, List<Candidate>> found) {
        for (int i = 0; i < Math.min(2, tokens.size()); i++) {
            if (kode(tokens.get(i).text()) != null && i + 1 < tokens.size()) {
                String title = validText(cellText(tokens.subList(i + 1, tokens.size())), Limits.JOB_TITLE, true);
                if (title != null) {
                    found.computeIfAbsent(Field.TITLE, k -> new ArrayList<>()).add(new Candidate(title, 0, index));
                }
                return;
            }
        }
    }

    private static boolean isFraction(Line line, Hit hit) {
        StringBuilder key = new StringBuilder();
        for (int k = hit.first(); k <= hit.last(); k++) {
            key.append(line.tokens().get(k).key());
        }
        return FRACTION_LABELS.contains(key.toString());
    }

    // ---- several positions on one slip ----

    /** The heading of a block of one position: the main one ("Hovedstilling") or a side one ("Tilleggsstilling"). */
    private record Marker(int line, boolean main) {
    }

    private static final Pattern MAIN_MARKER = Pattern.compile("^\\s*(?:hovedstilling|hovedarbeidsforhold)\\b", FLAGS);
    private static final Pattern SIDE_MARKER = Pattern.compile(
            "^\\s*(?:bistilling|tilleggsstilling|biarbeidsforhold|vikariat|stilling\\s*[12])\\b", FLAGS);

    private static List<Marker> markers(List<Line> lines) {
        List<Marker> markers = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i).raw();
            if (MAIN_MARKER.matcher(raw).find()) {
                markers.add(new Marker(i, true));
            } else if (SIDE_MARKER.matcher(raw).find()) {
                markers.add(new Marker(i, false));
            }
        }
        return markers;
    }

    /**
     * With two or more position blocks, keeps what sits in the one main block (or above the first block when the main
     * block has none of it). With two main blocks, or only side blocks, nothing in a block is used.
     */
    private static <T> List<T> pick(List<T> items, java.util.function.ToIntFunction<T> lineOf, List<Marker> markers) {
        if (markers.size() < 2) {
            return items;
        }
        List<Integer> mains = new ArrayList<>();
        for (int i = 0; i < markers.size(); i++) {
            if (markers.get(i).main()) {
                mains.add(i);
            }
        }
        if (mains.size() == 1) {
            List<T> inMain = items.stream().filter(item -> blockOf(markers, lineOf.applyAsInt(item)) == mains.get(0)).toList();
            if (!inMain.isEmpty()) {
                return inMain;
            }
        }
        if (mains.size() > 1) {
            return List.of();
        }
        return items.stream().filter(item -> blockOf(markers, lineOf.applyAsInt(item)) == -1).toList();
    }

    /** The number of the block a line sits in, or -1 above the first block. */
    private static int blockOf(List<Marker> markers, int line) {
        int block = -1;
        for (int i = 0; i < markers.size(); i++) {
            if (markers.get(i).line() <= line) {
                block = i;
            }
        }
        return block;
    }

    // ---- a plain "Årslønn" ----

    /**
     * A plain "Årslønn" is the full-time salary on some payslips and the person's own on others. It is only taken when
     * the position is 100 %, or when a monthly pay line equals the salary / 12 x the position percentage (to within
     * 1 kr). The check only decides whether to accept the printed figure; the figure itself is never calculated.
     */
    private static String plainSalary(String printed, String percent, List<Line> lines) {
        if (printed == null || percent == null) {
            return null;
        }
        int share = Integer.parseInt(percent);
        if (share == 100) {
            return printed;
        }
        BigDecimal expected = new BigDecimal(printed).multiply(BigDecimal.valueOf(share))
                .divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
        for (Line line : lines) {
            String plain = plain(line.raw());
            if (!plain.contains("manedslonn") && !plain.contains("fastlonn")) {
                continue;
            }
            Matcher matcher = MONTHLY_AMOUNT.matcher(line.raw());
            while (matcher.find()) {
                BigDecimal amount = new BigDecimal(matcher.group().replaceAll("[ .\\u00a0]", "").replace(',', '.'));
                if (amount.subtract(expected).abs().compareTo(BigDecimal.ONE) <= 0) {
                    return printed;
                }
            }
        }
        return null;
    }

    private static final Pattern MONTHLY_AMOUNT = Pattern.compile("\\d{1,3}(?:[ .\\u00a0]\\d{3})*,\\d{1,2}|\\d+,\\d{1,2}");

    private static int nextHitStart(Line line, Hit hit) {
        for (Hit other : line.hits()) {
            if (other.first() > hit.last()) {
                return other.first();
            }
        }
        return line.tokens().size();
    }

    private static List<Token> skipFiller(List<Token> region) {
        int from = 0;
        while (from < region.size() && region.get(from).key().isEmpty()) {
            from++;
        }
        return region.subList(from, region.size());
    }

    private String fromRegion(Field field, List<Token> region, boolean fraction) {
        List<Token> tokens = skipFiller(region);
        if (tokens.isEmpty()) {
            return null;
        }
        return switch (field) {
            case EMPLOYER, TITLE -> textFor(field, cellText(tokens));
            case PERCENT -> percentIn(tokens, fraction);
            case TRINN -> firstWhere(tokens, 2, PayslipRuleReader::trinn);
            case RAMMETRINN -> rammetrinn(joined(tokens.subList(0, Math.min(3, tokens.size()))));
            case KODE -> firstWhere(tokens, 2, PayslipRuleReader::kode);
            case DATE -> firstWhere(tokens, 3, text -> dateText(text, true)) != null
                    ? firstWhere(tokens, 3, text -> dateText(text, true))
                    : dateText(joined(tokens.subList(0, Math.min(3, tokens.size()))), true);
            case SALARY, SALARY_PLAIN -> salaryIn(joined(tokens));
        };
    }

    /** The text up to the first wide gap, a long number or the end of the line. A leading code is let through. */
    private static String cellText(List<Token> tokens) {
        StringBuilder text = new StringBuilder();
        Token previous = null;
        for (Token token : tokens) {
            if (previous != null && token.start() - previous.end() >= 3) {
                break;
            }
            if (previous != null && token.text().matches("\\d{4,}")) {
                break;
            }
            text.append(text.length() == 0 ? "" : " ").append(token.text());
            previous = token;
        }
        return text.toString();
    }

    private static String joined(List<Token> tokens) {
        StringBuilder text = new StringBuilder();
        for (Token token : tokens) {
            text.append(text.length() == 0 ? "" : " ").append(token.text());
        }
        return text.toString();
    }

    private static String firstWhere(List<Token> tokens, int within, java.util.function.Function<String, String> check) {
        for (int i = 0; i < Math.min(within, tokens.size()); i++) {
            String value = check.apply(tokens.get(i).text());
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String percentIn(List<Token> tokens, boolean fraction) {
        for (int i = 0; i < Math.min(3, tokens.size()); i++) {
            String value = percent(tokens.get(i).text(), fraction);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** Values on the rows below a row of column titles, taken from the columns that sit under the title. */
    private String alignedBelow(List<Line> lines, int index, Hit hit, java.util.Set<Integer> used) {
        Line header = lines.get(index);
        int from = header.tokens().get(hit.first()).start() - ALIGN_TOLERANCE;
        int to = header.tokens().get(hit.last()).end() + ALIGN_TOLERANCE;
        int centre = (header.tokens().get(hit.first()).start() + header.tokens().get(hit.last()).end()) / 2;
        int seen = 0;
        for (int i = index + 1; i < lines.size() && seen < 2; i++) {
            Line row = lines.get(i);
            if (row.tokens().isEmpty()) {
                continue;
            }
            seen++;
            if (hasOwnTitleInColumn(row, from, to)) {
                continue; // a row with its own title there is another key/value pair, not a row of values
            }
            String value = valueInRow(header, hit, row, from, to, centre);
            if (value != null) {
                used.add(i);
                // A table of three or more columns with two rows that disagree: nothing says which row counts.
                if (header.hits().size() >= 3 && differentValueInNextRow(lines, i, header, hit, value, from, to, centre)) {
                    return UNCLEAR;
                }
                return value;
            }
        }
        return null;
    }

    private String valueInRow(Line header, Hit hit, Line row, int from, int to, int centre) {
        boolean fraction = isFraction(header, hit);
        String sameCell = sameCellIndex(header, hit, row);
        if (sameCell != null) {
            String value = checkedFor(hit.field(), sameCell, fraction);
            if (value != null) {
                return value;
            }
        }
        if (hit.field() != Field.EMPLOYER && hit.field() != Field.TITLE) {
            String paired = pairedInRun(header, hit, row);
            if (paired != null) {
                String value = checkedFor(hit.field(), paired, fraction);
                if (value != null) {
                    return value;
                }
            }
        }
        for (String candidate : underneath(row, from, to, centre, hit.field())) {
            String value = checkedFor(hit.field(), candidate, fraction);
            if (value != null) {
                return value;
            }
        }
        // A row written with single spaces has no columns to line up: the n-th title goes with the n-th value.
        int position = header.hits().indexOf(hit);
        if (position >= 0 && header.hits().size() == row.tokens().size()) {
            return checkedFor(hit.field(), row.tokens().get(position).text(), fraction);
        }
        return null;
    }

    private boolean differentValueInNextRow(List<Line> lines, int rowIndex, Line header, Hit hit, String value,
                                            int from, int to, int centre) {
        for (int i = rowIndex + 1; i < lines.size(); i++) {
            Line next = lines.get(i);
            if (next.tokens().isEmpty()) {
                continue;
            }
            // Another row of the same table has as many cells as this one; a heading or a total line does not.
            int cells = cells(lines.get(rowIndex)).size();
            if (hasOwnTitleInColumn(next, from, to) || cells < 2 || cells(next).size() != cells) {
                return false;
            }
            String other = valueInRow(header, hit, next, from, to, centre);
            return other != null && !other.equalsIgnoreCase(value);
        }
        return false;
    }

    private static boolean hasOwnTitleInColumn(Line row, int from, int to) {
        for (Hit own : row.hits()) {
            if (row.tokens().get(own.last()).end() >= from && row.tokens().get(own.first()).start() <= to) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cells of a row are the pieces between gaps of three or more spaces. Two rows in different type sizes do not
     * line up character for character, but when the title row and the value row have the same number of cells, the
     * n-th title goes with the n-th value.
     */
    private static String sameCellIndex(Line header, Hit hit, Line row) {
        List<int[]> titleCells = cells(header);
        List<int[]> valueCells = cells(row);
        if (titleCells.size() < 2 || titleCells.size() != valueCells.size()) {
            return null;
        }
        int start = header.tokens().get(hit.first()).start();
        for (int i = 0; i < titleCells.size(); i++) {
            if (start >= titleCells.get(i)[0] && start < titleCells.get(i)[1]) {
                int[] cell = valueCells.get(i);
                return row.raw().substring(cell[0], cell[1]).strip();
            }
        }
        return null;
    }

    /** Start and end offsets of each cell of a line. */
    private static List<int[]> cells(Line line) {
        List<int[]> cells = new ArrayList<>();
        List<Token> tokens = line.tokens();
        int start = -1;
        int end = -1;
        for (Token token : tokens) {
            if (start >= 0 && token.start() - end >= 3) {
                cells.add(new int[] {start, end});
                start = -1;
            }
            if (start < 0) {
                start = token.start();
            }
            end = token.end();
        }
        if (start >= 0) {
            cells.add(new int[] {start, end});
        }
        return cells;
    }

    /**
     * Narrow columns sit so close that a value may be nearer the next title than its own ("St-% Ansiennitet L-ramme Alt
     * Ltr" over "0 01.12.2024 026 - 8 31"). When the titles in a tight run and the values under them are equally many,
     * the n-th title goes with the n-th value.
     */
    private static String pairedInRun(Line header, Hit hit, Line row) {
        List<Hit> hits = header.hits();
        int at = hits.indexOf(hit);
        int firstInRun = at;
        while (firstInRun > 0 && gapBetween(header, hits.get(firstInRun - 1), hits.get(firstInRun)) <= 3) {
            firstInRun--;
        }
        int lastInRun = at;
        while (lastInRun < hits.size() - 1 && gapBetween(header, hits.get(lastInRun), hits.get(lastInRun + 1)) <= 3) {
            lastInRun++;
        }
        int runSize = lastInRun - firstInRun + 1;
        if (runSize < 2) {
            return null;
        }
        int from = header.tokens().get(hits.get(firstInRun).first()).start() - ALIGN_TOLERANCE;
        int to = header.tokens().get(hits.get(lastInRun).last()).end() + ALIGN_TOLERANCE + 4;
        List<Token> values = row.tokens().stream()
                .filter(t -> !t.key().isEmpty() && (t.start() + t.end()) / 2 >= from && (t.start() + t.end()) / 2 <= to)
                .toList();
        return values.size() == runSize ? values.get(at - firstInRun).text() : null;
    }

    private static int gapBetween(Line line, Hit left, Hit right) {
        return line.tokens().get(right.first()).start() - line.tokens().get(left.last()).end();
    }

    /** What sits under the title, closest first: cells for text, single tokens for numbers. */
    private static List<String> underneath(Line row, int from, int to, int centre, Field field) {
        record Piece(String text, int distance) {
        }
        List<Piece> pieces = new ArrayList<>();
        if (field == Field.EMPLOYER || field == Field.TITLE) {
            int cellStart = -1;
            int cellEnd = -1;
            StringBuilder cell = new StringBuilder();
            Token previous = null;
            List<Token> tokens = row.tokens();
            for (int i = 0; i <= tokens.size(); i++) {
                Token token = i < tokens.size() ? tokens.get(i) : null;
                boolean breaks = token == null || (previous != null && token.start() - previous.end() >= 3);
                if (breaks && previous != null) {
                    if (cellEnd >= from && cellStart <= to) {
                        pieces.add(new Piece(cell.toString(), Math.abs((cellStart + cellEnd) / 2 - centre)));
                    }
                    cell.setLength(0);
                    cellStart = -1;
                }
                if (token != null) {
                    if (cellStart < 0) {
                        cellStart = token.start();
                    }
                    cellEnd = token.end();
                    cell.append(cell.length() == 0 ? "" : " ").append(token.text());
                    previous = token;
                }
            }
        } else {
            for (Token token : row.tokens()) {
                if (token.end() >= from && token.start() <= to) {
                    pieces.add(new Piece(token.text(), Math.abs((token.start() + token.end()) / 2 - centre)));
                }
            }
            if (field == Field.SALARY) {
                pieces.add(new Piece(joined(row.tokens().stream()
                        .filter(t -> t.end() >= from && t.start() <= to).toList()), 0));
            }
        }
        pieces.sort(Comparator.comparingInt(Piece::distance));
        return pieces.stream().map(Piece::text).toList();
    }

    private static String checkedFor(Field field, String text, boolean fraction) {
        return switch (field) {
            case EMPLOYER, TITLE -> textFor(field, text);
            case PERCENT -> percent(text, fraction);
            case TRINN -> trinn(text);
            case RAMMETRINN -> rammetrinn(text);
            case KODE -> kode(text);
            case DATE -> dateText(text, true);
            case SALARY, SALARY_PLAIN -> salaryIn(text);
        };
    }

    /** The text as written when it would be accepted (a title may start with its code: "7136 - Helsefagarbeider"). */
    private static String textFor(Field field, String raw) {
        String text = raw;
        if (field == Field.TITLE) {
            Matcher combined = CODE_AND_TITLE.matcher(raw);
            text = combined.matches() ? combined.group(2) : raw;
        }
        return validText(text, field == Field.TITLE ? Limits.JOB_TITLE : Limits.EMPLOYER, field == Field.TITLE) == null
                ? null : raw;
    }

    // ---- value checks ----

    private static final class Limits {
        static final int EMPLOYER = 100;
        static final int JOB_TITLE = 60;
    }

    /** Free text: form-safe characters, within the form's length, and a real word (not a number or a title). */
    private static String validText(String raw, int maxChars, boolean isTitle) {
        if (raw == null) {
            return null;
        }
        String value = raw.replaceAll("\\s+", " ").replaceAll("^[\\s:;,.\\-–|]+|[\\s:;,|]+$", "");
        long letters = value.chars().filter(Character::isLetter).count();
        long digits = value.chars().filter(Character::isDigit).count();
        if (value.length() < 2 || value.length() > maxChars || letters < 3 || digits > (isTitle ? 2 : 4)
                || !FORM_TEXT.matcher(value).matches()) {
            return null;
        }
        String firstWord = plain(value.split(" ")[0]);
        if (FUNCTION_WORDS.contains(firstWord) || NOT_A_NAME.contains(firstWord) || LABELS.containsKey(firstWord)
                || STOP_LABELS.contains(firstWord)) {
            return null;
        }
        if (isTitle && PAY_LINE_WORD.matcher(firstWord).matches()) {
            return null; // a pay line (Månedslønn, Kveldstillegg), not a job title
        }
        if (letters * 10 < value.length() * 6) {
            return null;
        }
        return value;
    }

    private static final Pattern FRACTION = Pattern.compile("^([01])[.,](\\d{1,3})$");

    /**
     * @param fraction the title says the share is written as a fraction of a full position ("Stillingsbrøk 0,5")
     */
    private static String percent(String raw, boolean fraction) {
        String text = digits(raw).replaceAll("[\\s\\u00a0]", "");
        if (fraction) {
            Matcher share = FRACTION.matcher(text);
            if (share.matches()) {
                BigDecimal whole = new BigDecimal(share.group(1) + "." + share.group(2)).movePointRight(2);
                int rounded = whole.setScale(0, RoundingMode.HALF_UP).intValue();
                return rounded == 0 ? "0" : rounded >= 5 && rounded <= 100 ? String.valueOf(rounded) : null;
            }
        }
        Matcher matcher = PERCENT.matcher(text);
        if (!matcher.matches()) {
            return null;
        }
        BigDecimal number = new BigDecimal(matcher.group(1) + (matcher.group(2) == null ? "" : "." + matcher.group(2)));
        if (number.signum() == 0) {
            return "0";
        }
        if (number.compareTo(BigDecimal.valueOf(100)) > 0) {
            return null;
        }
        int rounded = number.setScale(0, RoundingMode.HALF_UP).intValue();
        // A share under 5 % is not a position; "0,5" without a title that says fraction is more likely a mistake.
        return rounded >= 5 ? String.valueOf(rounded) : null;
    }

    private static String trinn(String raw) {
        String text = digits(raw);
        if (!text.matches("\\d{1,3}")) {
            return null;
        }
        int number = Integer.parseInt(text);
        return number >= 1 && number <= 100 ? String.valueOf(number) : null;
    }

    /** The code as the form takes it: up to six digits. A seven-digit number is not accepted. */
    private static String kode(String raw) {
        String text = digits(raw);
        return text.matches("\\d{1,6}") && !text.matches("0+") ? text : null;
    }

    private static final Pattern FRAME_AND_STEP = Pattern.compile("^(\\d{1,3})\\s*/\\s*(\\d{1,3})$");

    /** "26/45" under a title that names both the pay frame and the step: the step is the second number. */
    private static String rammetrinn(String raw) {
        Matcher matcher = FRAME_AND_STEP.matcher(digits(raw).strip());
        return matcher.matches() ? trinn(matcher.group(2)) : null;
    }

    private static final Pattern OCR_DIGITS = Pattern.compile("[0-9OoIl.,:%/-]+");

    /**
     * Text recognition reads 0 as O and 1 as l or I. In a whole token made only of digits, separators and those letters
     * (with at least one real digit) they are put back. Free text is never changed.
     */
    private static String digits(String text) {
        return TOKEN.matcher(text).replaceAll(match -> {
            String token = match.group();
            boolean number = OCR_DIGITS.matcher(token).matches() && token.chars().anyMatch(Character::isDigit);
            return Matcher.quoteReplacement(number
                    ? token.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1') : token);
        });
    }

    private static final Map<String, Integer> MONTHS = new HashMap<>();

    static {
        String[][] names = {{"januar", "jan", "january"}, {"februar", "feb", "february"}, {"mars", "mar", "march"},
                {"april", "apr"}, {"mai", "may"}, {"juni", "jun", "june"}, {"juli", "jul", "july"},
                {"august", "aug"}, {"september", "sep", "sept"}, {"oktober", "okt", "oct", "october"},
                {"november", "nov"}, {"desember", "des", "dec", "december"}};
        for (int i = 0; i < names.length; i++) {
            for (String name : names[i]) {
                MONTHS.put(name, i + 1);
            }
        }
    }

    private static final Pattern MONTH_NAME = Pattern.compile("(?<![\\p{L}\\d])(\\p{L}{3,9})\\.?\\s+(\\d{4})(?!\\d)", FLAGS);
    private static final Pattern SHORT_YEAR = Pattern.compile("(?<![\\d./-])(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2})(?![\\d./-])");

    /**
     * @param shortYear whether "01.03.18" is accepted. Only after a title that names the seniority date, never in a row
     *                  without titles.
     */
    private static String dateText(String raw, boolean shortYear) {
        String text = digits(raw);
        Matcher matcher = DATE.matcher(text);
        if (matcher.find()) {
            YearMonth month = matcher.group(3) != null
                    ? tryMonth(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(2)))
                    : tryMonth(Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)));
            return month == null ? null : month.toString();
        }
        Matcher named = MONTH_NAME.matcher(text);
        while (named.find()) {
            Integer month = MONTHS.get(plain(named.group(1)));
            if (month != null) {
                YearMonth value = tryMonth(Integer.parseInt(named.group(2)), month);
                return value == null ? null : value.toString();
            }
        }
        Matcher two = SHORT_YEAR.matcher(text);
        if (shortYear && two.find()) {
            int year = 2000 + Integer.parseInt(two.group(3));
            if (year > YearMonth.now().getYear()) {
                year -= 100;
            }
            YearMonth value = tryMonth(year, Integer.parseInt(two.group(2)));
            return value == null ? null : value.toString();
        }
        return null;
    }

    private static YearMonth tryMonth(int year, int month) {
        if (month < 1 || month > 12) {
            return null;
        }
        YearMonth value = YearMonth.of(year, month);
        return value.isBefore(EARLIEST) || value.isAfter(YearMonth.now()) ? null : value;
    }

    private static String salaryIn(String text) {
        Matcher matcher = AMOUNT.matcher(digits(text));
        while (matcher.find()) {
            String whole = matcher.group(1).replaceAll("[ .\\u00a0]", "");
            if (whole.length() <= 7) {
                long amount = Long.parseLong(whole);
                if (amount >= 10_000 && amount <= MAX_SALARY) {
                    return String.valueOf(amount);
                }
            }
        }
        return null;
    }

    // ---- rows without column titles, and the letterhead ----

    /** A row that holds a code, a title, a percentage and a date in that order: {code, title, percent, date}. */
    private static List<String[]> shapeRows(List<Line> lines) {
        List<String[]> rows = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            if (payTableAbove(lines, lineIndex)) {
                continue; // the rows under "Lønnsart Tekst Antall Sats Beløp" are pay lines, not a position
            }
            List<Token> tokens = lines.get(lineIndex).tokens();
            for (int i = 0; i < tokens.size(); i++) {
                String code = kode(tokens.get(i).text());
                if (code == null || code.length() < 4) {
                    continue;
                }
                StringBuilder title = new StringBuilder();
                String percent = null;
                String date = null;
                for (int j = i + 1; j < tokens.size(); j++) {
                    String text = tokens.get(j).text();
                    boolean percentSign = j + 1 < tokens.size() && tokens.get(j + 1).text().equals("%");
                    if (percent == null && date == null && text.matches("\\p{L}[\\p{L}.'-]*")) {
                        title.append(title.length() == 0 ? "" : " ").append(text);
                    } else if (percent == null && date == null
                            && (text.matches("\\d{1,3}[.,]\\d{1,2}") || (percentSign && text.matches("\\d{1,3}")))) {
                        percent = percent(text, false);
                    } else if (percent != null && date == null && dateText(text, false) != null) {
                        date = dateText(text, false);
                    }
                }
                String validTitle = validText(title.toString(), Limits.JOB_TITLE, true);
                if (validTitle != null && percent != null && date != null && !"0".equals(percent)) {
                    rows.add(new String[] {code, validTitle, percent, date, String.valueOf(lineIndex)});
                    break;
                }
            }
        }
        return rows;
    }

    /** Whether one of the few lines above is the title row of a pay-line table (two of Lønnsart, Tekst, Antall, ...). */
    private static boolean payTableAbove(List<Line> lines, int lineIndex) {
        int looked = 0;
        for (int i = lineIndex - 1; i >= 0 && looked < 3; i--) {
            if (lines.get(i).tokens().isEmpty()) {
                continue;
            }
            looked++;
            long titles = lines.get(i).tokens().stream().filter(t -> PAY_TABLE_TITLES.contains(t.key())).count();
            if (titles >= 2) {
                return true;
            }
        }
        return false;
    }

    /** An organisation name on its own line near the top ("Oslo kommune"), for payslips without an employer label. */
    private static String letterhead(List<Line> lines, java.util.Set<Integer> used) {
        int checked = 0;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String text = lines.get(lineIndex).raw().strip();
            if (text.isEmpty()) {
                continue;
            }
            text = GAP.split(text)[0]; // a screenshot can put stray marks beside the name
            if (++checked > 12) {
                break;
            }
            if (startsWithTitle(lines.get(lineIndex)) || isPayrollProvider(text)) {
                continue; // "Skattekommune Testby kommune", or a payroll bureau's letterhead
            }
            boolean companyForm = checked <= 3 && COMPANY_FORM.matcher(text).find();
            if (text.length() <= 40 && text.chars().noneMatch(Character::isDigit)
                    && (EMPLOYER_NAME.matcher(text).find() || companyForm)) {
                String name = validText(text, Limits.EMPLOYER, false);
                if (name != null) {
                    used.add(lineIndex);
                }
                return name;
            }
        }
        return null;
    }

    /** A line that starts with one of our titles or stop titles is a labelled value, not a letterhead. */
    private static boolean startsWithTitle(Line line) {
        return !line.tokens().isEmpty() && (LABELS.containsKey(line.tokens().get(0).key())
                || STOP_LABELS.contains(line.tokens().get(0).key()));
    }

    private static boolean isPayrollProvider(String text) {
        String key = plain(text);
        if (PAYROLL_PROVIDERS.stream().anyMatch(key::contains)) {
            return true;
        }
        return TOKEN.matcher(text).results().anyMatch(word -> PAYROLL_PROVIDER_WORDS.contains(plain(word.group())));
    }

    // ---- title spotting ----

    private static List<Token> tokens(String raw) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(raw);
        while (matcher.find()) {
            tokens.add(new Token(matcher.group(), matcher.start(), matcher.end(), plain(matcher.group())));
        }
        return tokens;
    }

    private static List<Hit> spot(List<Token> tokens) {
        List<Hit> hits = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            Hit hit = null;
            for (int n = Math.min(MAX_WINDOW, tokens.size() - i); n >= 1 && hit == null; n--) {
                StringBuilder key = new StringBuilder();
                for (int k = i; k < i + n; k++) {
                    key.append(tokens.get(k).key());
                }
                hit = match(key.toString(), i, i + n - 1);
            }
            if (hit != null) {
                hits.addAll(split(hit, tokens));
                i = hit.last() + 1;
            } else {
                i++;
            }
        }
        return hits;
    }

    /** "Stillingskode - Tittel" is two titles in one: the code, then the job title. */
    private static List<Hit> split(Hit hit, List<Token> tokens) {
        StringBuilder key = new StringBuilder();
        for (int k = hit.first(); k <= hit.last(); k++) {
            key.append(tokens.get(k).key());
        }
        if (CODE_AND_TITLE_LABELS.contains(key.toString())) {
            return List.of(new Hit(Field.KODE, hit.first(), hit.first(), 2), new Hit(Field.TITLE, hit.last(), hit.last(), 2));
        }
        return List.of(hit);
    }

    private static Hit match(String key, int first, int last) {
        if (key.isEmpty()) {
            return null;
        }
        if (CODE_AND_TITLE_LABELS.contains(key)) {
            return new Hit(Field.TITLE, first, last, 2);
        }
        Field field = LABELS.get(key);
        if (field != null) {
            return new Hit(field, first, last, strength(key));
        }
        if (STOP_LABELS.contains(key)) {
            return new Hit(null, first, last, 0);
        }
        if (key.length() >= 9) {
            for (Map.Entry<String, Field> entry : LABELS.entrySet()) {
                if (entry.getKey().length() >= 9 && Math.abs(entry.getKey().length() - key.length()) <= 1
                        && distance(key, entry.getKey()) <= 1) {
                    return new Hit(entry.getValue(), first, last, strength(entry.getKey()));
                }
            }
        }
        if (key.length() >= 12) {
            // Two recognition mistakes in a long title ("Stlllngsprosent"): only when one single field is that close.
            Field only = null;
            String closest = null;
            boolean clash = false;
            for (Map.Entry<String, Field> entry : LABELS.entrySet()) {
                if (entry.getKey().length() >= 12 && Math.abs(entry.getKey().length() - key.length()) <= 2
                        && digitCount(key) == digitCount(entry.getKey()) && distance(key, entry.getKey()) <= 2) {
                    if (only != null && only != entry.getValue()) {
                        clash = true;
                    }
                    only = entry.getValue();
                    closest = entry.getKey();
                }
            }
            if (only != null && !clash) {
                return new Hit(only, first, last, strength(closest));
            }
        }
        return null;
    }

    private static long digitCount(String key) {
        return key.chars().filter(Character::isDigit).count();
    }

    private static int strength(String key) {
        return WEAK_LABELS.contains(key) ? 1 : 2;
    }

    private static String plain(String word) {
        String lower = word.toLowerCase(Locale.ROOT)
                .replace('ø', 'o').replace('ö', 'o').replace('å', 'a').replace('ä', 'a').replace('æ', 'a')
                .replace('é', 'e').replace('è', 'e');
        // text recognition reads 0 as O inside a title like "Årslønn 1OO %"
        return lower.replaceAll("[^a-z0-9%]", "").replaceAll("(?<=\\d)o|o(?=[\\d%])", "0");
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private static String best(Map<Field, List<Candidate>> found, Field field) {
        List<Candidate> candidates = found.get(field);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .min(Comparator.comparingInt((Candidate c) -> -c.strength()).thenComparingInt(Candidate::line))
                .map(Candidate::value).orElse(null);
    }

    private static Integer intOrNull(String value) {
        return value == null ? null : Integer.valueOf(value);
    }

    private static YearMonth yearMonth(String value) {
        return value == null ? null : YearMonth.parse(value);
    }

    private static BigDecimal amountOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
