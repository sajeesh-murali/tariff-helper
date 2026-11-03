package com.tariffhelper.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Cuts a payslip's text down to the few lines that can hold the three form fields, and masks personal numbers in
 * them, before anything is sent to an LLM. A payslip carries the national ID number, bank account, name and address,
 * and none of that is needed.
 *
 * It keeps only lines that mention a keyword (and the two lines after, because the value is often on the row
 * below its label), drops lines that carry a personal label, and masks ID, account, phone and email patterns.
 * A name that stands on a kept line without a label cannot be recognised; keeping the excerpt this small is the main
 * protection.
 */
@Component
public class PayslipRedactor {

    /** The labels the excerpt is built around, as plain letters (æ, ø, å written as a, o, a). */
    private static final List<String> LABELS = List.of(
            "arbeidsgiver", "stillingsprosent", "stillingskode", "ansiennitet", "lonnstrinn", "arslonn", "stilling",
            "prosent");

    static final int MAX_LINES = 30;
    static final int MAX_LINE_CHARS = 160;
    static final String MASK = "[removed]";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern KEYWORD = Pattern.compile(
            "stilling|l[øo]nn?strinn|trinn|ansienn|kode|arbeidsgiver|[åa]rsl[øo]nn|prosent", FLAGS);
    // A letterhead can name the employer without any label ("Oslo kommune"). Only a short line without digits is kept,
    // and never the lines around it, because those hold the person's name and address.
    private static final Pattern EMPLOYER_NAME = Pattern.compile(
            "\\b(?:kommune|fylkeskommune|helseforetak|sykehus|hf)\\b", FLAGS);
    private static final int MAX_EMPLOYER_NAME_CHARS = 40;

    // The employer line may say "navn" (Arbeidsgivernavn), so a name label only drops lines that are not about the employer.
    private static final Pattern EMPLOYER = Pattern.compile("arbeidsgiver", FLAGS);
    private static final Pattern NAME_LABEL = Pattern.compile("navn|name", FLAGS);
    private static final Pattern PERSONAL_LABEL = Pattern.compile(
            "f[øo]dsel|f[øo]dt|birth|adresse|address|konto|account|bank|iban|telefon|phone|e-?post|mail|fnr", FLAGS);
    private static final List<Pattern> MASKED = List.of(
            // a street address ("Storgata 1 B") and a line that is only a postcode and a place ("0123 OSLO")
            Pattern.compile("\\p{L}[\\p{L} .'-]*?(?:gate|gata|gaten|vei|veien|veg|vegen|all[ée]|plass|torg|stien)\\s+\\d+\\s?[A-Za-z]?(?![\\p{L}\\d])",
                    FLAGS),
            Pattern.compile("^\\d{4} \\p{Lu}[\\p{Lu} -]+$"),
            Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+"),
            Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\\b"),
            Pattern.compile("(?<!\\d)\\d{6}[ .]?\\d{5}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{4}[. ]\\d{2}[. ]\\d{5}(?!\\d)"),
            Pattern.compile("(?:\\+|00)\\d{2} ?\\d{8}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{3} \\d{2} \\d{3}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{8}(?!\\d)"));

    // "Etternavn, Fornavn" (one or two given names) as payslips print a name; it can stand on a line that is kept for another word.
    private static final Pattern PERSON_NAME = Pattern.compile(
            "\\p{Lu}[\\p{L}'-]+(?: \\p{Lu}[\\p{L}'-]+)*, \\p{Lu}[\\p{L}'-]+(?: \\p{Lu}[\\p{L}'-]+)?");
    // Text recognition keeps the wide gaps between columns, so a gap of three or more spaces separates two cells.
    private static final Pattern CELL_GAP = Pattern.compile("\\s{3,}");

    /** The text to send to the LLM; empty when the payslip has no line worth sending. */
    public String excerpt(String payslipText) {
        return excerpt(payslipText, java.util.Set.of());
    }

    /**
     * @param alsoKeep numbers of lines the rules in code read values or titles from, kept even when no keyword is
     *                 near them, so a row with no titles still reaches the model
     */
    public String excerpt(String payslipText, java.util.Set<Integer> alsoKeep) {
        String[] lines = payslipText.split("\\R");
        TreeSet<Integer> keep = new TreeSet<>();
        for (int index : alsoKeep) {
            if (index >= 0 && index < lines.length) {
                keep.add(index);
            }
        }
        for (int i = 0; i < lines.length; i++) {
            if (KEYWORD.matcher(lines[i]).find() || looksLikeALabel(lines[i])) {
                for (int j = i; j <= i + 2 && j < lines.length; j++) {
                    keep.add(j);
                }
            } else if (looksLikeAnEmployerName(lines[i])) {
                keep.add(i);
            }
        }
        List<String> out = new ArrayList<>();
        for (int index : keep) {
            String source = lines[index];
            if (index > 0 && isRowOfLabels(lines[index - 1])) {
                // A row of labels with the value row below it (name, employer, ID number side by side): only the
                // values under a wanted label are kept. If the columns do not line up, the row is left out.
                source = valuesUnderWantedLabels(lines[index - 1], source);
            }
            String line = PERSON_NAME.matcher(source.replaceAll("\\s+", " ").strip()).replaceAll(MASK);
            if (line.isEmpty() || PERSONAL_LABEL.matcher(line).find()
                    || (NAME_LABEL.matcher(line).find() && !EMPLOYER.matcher(line).find())) {
                continue;
            }
            String masked = mask(line.length() <= MAX_LINE_CHARS ? line : line.substring(0, MAX_LINE_CHARS));
            if (masked.equals(MASK)) {
                continue;
            }
            out.add(masked);
            if (out.size() == MAX_LINES) {
                break;
            }
        }
        return String.join("\n", out);
    }

    /** A row of column titles that names a person-related column, for example "Lønnsmottaker ... Fødselsnr.". */
    static boolean isRowOfLabels(String line) {
        return cells(line).length >= 2 && PERSONAL_LABEL.matcher(line).find();
    }

    /**
     * Pairs each value with the title above it and keeps only those whose title is wanted ("Arbeidsgivers navn"), so a
     * name or number in the next column cannot slip through. When the cells do not line up one to one, the row is only
     * kept if it names a wanted label itself.
     */
    static String valuesUnderWantedLabels(String labelRow, String valueRow) {
        String[] labels = cells(labelRow);
        String[] values = cells(valueRow);
        if (labels.length != values.length) {
            return KEYWORD.matcher(valueRow).find() ? valueRow : "";
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            if (!PERSONAL_LABEL.matcher(labels[i]).find()
                    && (KEYWORD.matcher(labels[i]).find() || looksLikeALabel(labels[i]))) {
                kept.add(labels[i] + ": " + values[i]);
            }
        }
        return String.join(" ", kept);
    }

    private static String[] cells(String line) {
        String text = line.strip();
        return text.isEmpty() ? new String[0] : CELL_GAP.split(text);
    }

    /**
     * Text recognition often gets a letter wrong ("Arbeidsg1ver", "Ansiennltet"). A word within one or two letters of a
     * label still marks its line, so the excerpt does not come out empty for a scan. Only the label is guessed; the
     * values are still copied exactly as written, and the same masking and personal-label rules apply.
     */
    static boolean looksLikeAnEmployerName(String line) {
        String text = line.strip();
        return !text.isEmpty() && text.length() <= MAX_EMPLOYER_NAME_CHARS
                && text.chars().noneMatch(Character::isDigit) && EMPLOYER_NAME.matcher(text).find();
    }

    static boolean looksLikeALabel(String line) {
        for (String word : line.split("[^\\p{L}\\p{N}]+")) {
            String plain = plain(word);
            long digits = plain.chars().filter(Character::isDigit).count();
            // a recognised label may hold a wrong digit ("Arbeidsg1ver"), but a number is not a label
            if (plain.length() < 5 || digits > 2 || plain.length() - digits < 4) {
                continue;
            }
            for (String label : LABELS) {
                int allowed = label.length() >= 8 ? 2 : 1;
                if (Math.abs(plain.length() - label.length()) <= allowed && distance(plain, label) <= allowed) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String plain(String word) {
        return word.toLowerCase(java.util.Locale.ROOT)
                .replace('ø', 'o').replace('ö', 'o').replace('å', 'a').replace('ä', 'a').replace('æ', 'a');
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

    private static String mask(String line) {
        String result = line;
        for (Pattern pattern : MASKED) {
            result = pattern.matcher(result).replaceAll(MASK);
        }
        return result;
    }
}
