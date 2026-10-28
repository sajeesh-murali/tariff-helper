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

    static final int MAX_LINES = 30;
    static final int MAX_LINE_CHARS = 160;
    static final String MASK = "[removed]";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern KEYWORD = Pattern.compile(
            "stilling|l[øo]nn?strinn|trinn|ansienn|kode|arbeidsgiver|[åa]rsl[øo]nn|prosent", FLAGS);
    // The employer line may say "navn" (Arbeidsgivernavn), so a name label only drops lines that are not about the employer.
    private static final Pattern EMPLOYER = Pattern.compile("arbeidsgiver", FLAGS);
    private static final Pattern NAME_LABEL = Pattern.compile("navn|name", FLAGS);
    private static final Pattern PERSONAL_LABEL = Pattern.compile(
            "f[øo]dsel|f[øo]dt|birth|adresse|address|konto|account|bank|iban|telefon|phone|e-?post|mail|fnr", FLAGS);
    private static final List<Pattern> MASKED = List.of(
            Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+"),
            Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\\b"),
            Pattern.compile("(?<!\\d)\\d{6}[ .]?\\d{5}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{4}[. ]\\d{2}[. ]\\d{5}(?!\\d)"),
            Pattern.compile("(?:\\+|00)\\d{2} ?\\d{8}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{3} \\d{2} \\d{3}(?!\\d)"),
            Pattern.compile("(?<!\\d)\\d{8}(?!\\d)"));

    /** The text to send to the LLM; empty when the payslip has no line worth sending. */
    public String excerpt(String payslipText) {
        String[] lines = payslipText.split("\\R");
        TreeSet<Integer> keep = new TreeSet<>();
        for (int i = 0; i < lines.length; i++) {
            if (KEYWORD.matcher(lines[i]).find()) {
                for (int j = i; j <= i + 2 && j < lines.length; j++) {
                    keep.add(j);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (int index : keep) {
            String line = lines[index].replaceAll("\\s+", " ").strip();
            if (line.isEmpty() || PERSONAL_LABEL.matcher(line).find()
                    || (NAME_LABEL.matcher(line).find() && !EMPLOYER.matcher(line).find())) {
                continue;
            }
            out.add(mask(line.length() <= MAX_LINE_CHARS ? line : line.substring(0, MAX_LINE_CHARS)));
            if (out.size() == MAX_LINES) {
                break;
            }
        }
        return String.join("\n", out);
    }

    private static String mask(String line) {
        String result = line;
        for (Pattern pattern : MASKED) {
            result = pattern.matcher(result).replaceAll(MASK);
        }
        return result;
    }
}
