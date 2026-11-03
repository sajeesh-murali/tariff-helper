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

    enum Field { EMPLOYER, TITLE, PERCENT, SALARY, TRINN, KODE, DATE }

    private static final Map<String, Field> LABELS = new HashMap<>();
    /** Titles of other columns. They only mark where a value ends. */
    private static final List<String> STOP_LABELS = List.of(
            "organisasjonsnr", "orgnr", "orgenhet", "orgenhetsnr", "ansattnr", "ansattnummer", "ressursnummer",
            "avtaleomrade", "periode", "periodenr", "utbetalt", "utbetalingsdato", "fodselsnr", "fodselsnummer",
            "fodselsdato", "konto", "lonnskonto", "adresse", "skattekort", "lonnsansiennitet", "lonnsramme", "lramme",
            "lr", "alt", "navn", "arbeidssted", "utlonningssted", "lonnsmottaker", "juridiskorganisasjonsnr",
            "arbeidsgivernr", "tab", "kode", "lonntrekk", "betegnelse2", "stillingsprosent2");

    static {
        add(Field.EMPLOYER, "arbeidsgiver", "arbeidsgivernavn", "arbeidsgiversnavn", "virksomhet", "arbeidsgiverennavn");
        add(Field.TITLE, "stilling", "stillingstittel", "stillingsbetegnelse", "betegnelse", "tittel", "yrke",
                "stillingstitel", "stillingsnavn");
        add(Field.PERCENT, "stillingsprosent", "stillingsandel", "stillingsbrok", "st%", "stilling%", "stprosent",
                "sysselsettingsgrad", "stillingprosent", "stillingsprosenten", "stillingsstorrelse");
        add(Field.SALARY, "arslonn100%", "arslonni100%stilling", "arslonni100%", "arslonnfulltid", "heltidsarslonn",
                "arslonnheltid", "arslonn100prosent", "arslonni100prosentstilling", "arslonnfor100%");
        add(Field.TRINN, "lonnstrinn", "ltrinn", "ltr", "lonnstr", "trinn", "lonnstrinnet");
        add(Field.KODE, "stillingskode", "stkode", "stillkode", "stillingskd", "stillingskoden", "stkd");
        add(Field.DATE, "ansiennitet", "ansiennitetsdato", "ansdato", "ansiennitetfra", "registrertansiennitet",
                "ansiennitetsdatoen", "ans", "ansiennitetsdatopalonnsslippen", "ansienn");
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
    private static final Pattern CODE_AND_TITLE = Pattern.compile("^\\s*[A-Za-z]?(\\d{4,7})\\s*[-–]\\s*(.+)$");
    private static final Pattern EMPLOYER_NAME = Pattern.compile(
            "\\b(?:kommune|fylkeskommune|helseforetak|sykehus|hf|direktorat|universitet|h[øo]gskole|h[øo]yskole)\\b", FLAGS);
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
        for (int i = 0; i < lines.size(); i++) {
            for (Hit hit : lines.get(i).hits()) {
                if (hit.field() != null) {
                    collect(lines, i, hit, found, notPrinted, used);
                }
            }
        }
        String code = best(found, Field.KODE);
        String title = best(found, Field.TITLE);
        String percent = best(found, Field.PERCENT);
        String date = best(found, Field.DATE);
        String[] shape = shapeRow(lines);
        if (shape != null) {
            used.add(Integer.parseInt(shape[4]));
            code = code == null ? shape[0] : code;
            title = title == null ? shape[1] : title;
            percent = percent == null ? shape[2] : percent;
            date = date == null ? shape[3] : date;
        }
        String employer = best(found, Field.EMPLOYER);
        if (employer == null) {
            employer = letterhead(lines, used);
        }
        PayslipFields fields = new PayslipFields(code, intOrNull(best(found, Field.TRINN)), yearMonth(date),
                employer, title, intOrNull(percent), amountOrNull(best(found, Field.SALARY)));
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

    private void collect(List<Line> lines, int index, Hit hit, Map<Field, List<Candidate>> found,
                         Map<String, String> notPrinted, java.util.Set<Integer> used) {
        Line line = lines.get(index);
        int next = nextHitStart(line, hit);
        List<Token> region = line.tokens().subList(hit.last() + 1, next);
        String value = fromRegion(hit.field(), region, line);
        if (value == null && skipFiller(region).isEmpty()) {
            value = alignedBelow(lines, index, hit, used);
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
        }
        if (value != null) {
            found.computeIfAbsent(hit.field(), k -> new ArrayList<>()).add(new Candidate(value, hit.strength(), index));
            used.add(index);
        }
    }

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

    private String fromRegion(Field field, List<Token> region, Line line) {
        List<Token> tokens = skipFiller(region);
        if (tokens.isEmpty()) {
            return null;
        }
        return switch (field) {
            case EMPLOYER, TITLE -> textFor(field, cellText(tokens));
            case PERCENT -> percentIn(tokens);
            case TRINN -> firstWhere(tokens, 2, PayslipRuleReader::trinn);
            case KODE -> firstWhere(tokens, 2, PayslipRuleReader::kode);
            case DATE -> firstWhere(tokens, 3, PayslipRuleReader::dateText);
            case SALARY -> salaryIn(joined(tokens));
        };
    }

    /** The text up to the first wide gap, a long number or the end of the line. */
    private static String cellText(List<Token> tokens) {
        StringBuilder text = new StringBuilder();
        Token previous = null;
        for (Token token : tokens) {
            if (previous != null && token.start() - previous.end() >= 3) {
                break;
            }
            if (token.text().matches("\\d{4,}") ) {
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

    private static String percentIn(List<Token> tokens) {
        for (int i = 0; i < Math.min(3, tokens.size()); i++) {
            String value = percent(tokens.get(i).text());
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
            String sameCell = sameCellIndex(header, hit, row);
            if (sameCell != null) {
                String value = checkedFor(hit.field(), sameCell);
                if (value != null) {
                    used.add(i);
                    return value;
                }
            }
            if (hit.field() != Field.EMPLOYER && hit.field() != Field.TITLE) {
                String paired = pairedInRun(header, hit, row);
                if (paired != null) {
                    String value = checkedFor(hit.field(), paired);
                    if (value != null) {
                        used.add(i);
                        return value;
                    }
                }
            }
            List<String> candidates = underneath(row, from, to, centre, hit.field());
            for (String candidate : candidates) {
                String value = checkedFor(hit.field(), candidate);
                if (value != null) {
                    used.add(i);
                    return value;
                }
            }
            // A row written with single spaces has no columns to line up: the n-th title goes with the n-th value.
            int position = header.hits().indexOf(hit);
            if (position >= 0 && header.hits().size() == row.tokens().size()) {
                String value = checkedFor(hit.field(), row.tokens().get(position).text());
                if (value != null) {
                    used.add(i);
                    return value;
                }
            }
        }
        return null;
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

    private static String checkedFor(Field field, String text) {
        return switch (field) {
            case EMPLOYER, TITLE -> textFor(field, text);
            case PERCENT -> percent(text);
            case TRINN -> trinn(text);
            case KODE -> kode(text);
            case DATE -> dateText(text);
            case SALARY -> salaryIn(text);
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
        if (letters * 10 < value.length() * 6) {
            return null;
        }
        return value;
    }

    private static String percent(String text) {
        Matcher matcher = PERCENT.matcher(text.replace(" ", ""));
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
        return rounded >= 1 ? String.valueOf(rounded) : null;
    }

    private static String trinn(String text) {
        if (!text.matches("\\d{1,3}")) {
            return null;
        }
        int number = Integer.parseInt(text);
        return number >= 1 && number <= 100 ? String.valueOf(number) : null;
    }

    /** The code as the form takes it: up to six digits. A seven-digit number is not accepted. */
    private static String kode(String text) {
        return text.matches("\\d{1,6}") && !text.matches("0+") ? text : null;
    }

    private static String dateText(String text) {
        Matcher matcher = DATE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        YearMonth month = matcher.group(3) != null
                ? tryMonth(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(2)))
                : tryMonth(Integer.parseInt(matcher.group(4)), Integer.parseInt(matcher.group(5)));
        return month == null ? null : month.toString();
    }

    private static YearMonth tryMonth(int year, int month) {
        if (month < 1 || month > 12) {
            return null;
        }
        YearMonth value = YearMonth.of(year, month);
        return value.isBefore(EARLIEST) || value.isAfter(YearMonth.now()) ? null : value;
    }

    private static String salaryIn(String text) {
        Matcher matcher = AMOUNT.matcher(text);
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
    private static String[] shapeRow(List<Line> lines) {
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
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
                    if (percent == null && date == null && text.matches("\\p{L}[\\p{L}.'-]*")) {
                        title.append(title.length() == 0 ? "" : " ").append(text);
                    } else if (percent == null && date == null && text.matches("\\d{1,3}[.,]\\d{1,2}")) {
                        percent = percent(text);
                    } else if (percent != null && date == null && dateText(text) != null) {
                        date = dateText(text);
                    }
                }
                String validTitle = validText(title.toString(), Limits.JOB_TITLE, true);
                if (validTitle != null && percent != null && date != null && !"0".equals(percent)) {
                    return new String[] {code, validTitle, percent, date, String.valueOf(lineIndex)};
                }
            }
        }
        return null;
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
            if (text.length() <= 40 && text.chars().noneMatch(Character::isDigit) && EMPLOYER_NAME.matcher(text).find()) {
                String name = validText(text, Limits.EMPLOYER, false);
                if (name != null) {
                    used.add(lineIndex);
                }
                return name;
            }
        }
        return null;
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
        if (key.toString().equals("stillingskodetittel")) {
            return List.of(new Hit(Field.KODE, hit.first(), hit.first(), 2), new Hit(Field.TITLE, hit.last(), hit.last(), 2));
        }
        return List.of(hit);
    }

    private static Hit match(String key, int first, int last) {
        if (key.isEmpty()) {
            return null;
        }
        if (key.equals("stillingskodetittel")) {
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
        return null;
    }

    private static int strength(String key) {
        return key.equals("stilling") || key.equals("trinn") || key.equals("ans") || key.equals("ltr") ? 1 : 2;
    }

    private static String plain(String word) {
        String lower = word.toLowerCase(Locale.ROOT)
                .replace('ø', 'o').replace('ö', 'o').replace('å', 'a').replace('ä', 'a').replace('æ', 'a')
                .replace('é', 'e').replace('è', 'e');
        return lower.replaceAll("[^a-z0-9%]", "");
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
