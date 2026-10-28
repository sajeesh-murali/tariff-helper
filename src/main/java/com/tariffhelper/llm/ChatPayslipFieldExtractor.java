package com.tariffhelper.llm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tariffhelper.api.FormText;
import com.tariffhelper.domain.PayslipFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the configured model to copy the payslip values out of a redacted excerpt. Each value is checked on its own:
 * it must be in the allowed range (free text must pass the form's character whitelist) and must literally appear in
 * the excerpt that was sent, so the model cannot make a value up or pass one along from an injected instruction.
 * A value that fails is left null. This never throws.
 *
 * Privacy: the excerpt and the model's answer are never logged, only which check failed.
 */
public class ChatPayslipFieldExtractor implements PayslipFieldExtractor {

    private static final Logger log = LoggerFactory.getLogger(ChatPayslipFieldExtractor.class);

    static final int MAX_TOKENS = 400;
    // Same limits as the estimate form, so a value accepted here is accepted there.
    static final int MAX_EMPLOYER_CHARS = 100;
    static final int MAX_JOB_TITLE_CHARS = 60;
    static final long MAX_ANNUAL_SALARY = 5_000_000L;

    private static final YearMonth EARLIEST = YearMonth.of(1950, 1);
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*(.*?)\\s*```$", Pattern.DOTALL);
    private static final Pattern STILLINGSKODE = Pattern.compile("^\\d{1,6}$");
    private static final Pattern YEAR_MONTH = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern FORM_TEXT = Pattern.compile(FormText.REGEX);
    // An amount as printed on a payslip: 612 300,00 / 612.300,00 / 612300. Group 1 is the whole-NOK part.
    private static final Pattern AMOUNT = Pattern.compile(
            "(?<![\\d,])(\\d{1,3}(?:[ .\\u00a0]\\d{3})+|\\d+)(?:,\\d{1,2})?(?!\\d)");

    private final ChatApi api;
    private final PromptBuilder prompts;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public ChatPayslipFieldExtractor(ChatApi api, PromptBuilder prompts) {
        this.api = api;
        this.prompts = prompts;
    }

    @Override
    public PayslipFields extract(String redactedExcerpt) {
        String answer;
        try {
            answer = api.send(prompts.payslipSystemPrompt(), prompts.payslipUserPrompt(redactedExcerpt), MAX_TOKENS);
        } catch (RuntimeException e) {
            log.warn("Payslip extraction failed, returning nothing: {}", e.getClass().getSimpleName());
            return PayslipFields.empty();
        }
        PayslipFields result = parse(answer, redactedExcerpt);
        log.info("Payslip extraction: model={}, fieldsAccepted={}", api.model(), result.foundCount());
        return result;
    }

    PayslipFields parse(String answer, String excerpt) {
        if (answer == null) {
            return PayslipFields.empty();
        }
        String text = answer.strip();
        var fenced = CODE_FENCE.matcher(text);
        if (fenced.matches()) {
            text = fenced.group(1);
        }
        JsonNode json;
        try {
            json = mapper.readTree(text);
        } catch (Exception e) {
            return rejected("not valid JSON");
        }
        if (json == null || !json.isObject()) {
            return rejected("not a JSON object");
        }
        return new PayslipFields(
                stillingskode(json.get("stillingskode"), excerpt),
                lonnstrinn(json.get("lonnstrinn"), excerpt),
                ansiennitetsdato(json.get("ansiennitetsdato"), excerpt),
                freeText("employerName", json.get("employerName"), MAX_EMPLOYER_CHARS, excerpt),
                freeText("jobTitle", json.get("jobTitle"), MAX_JOB_TITLE_CHARS, excerpt),
                positionPercent(json.get("positionPercent"), excerpt),
                annualSalaryFullTime(json.get("annualSalaryFullTime"), excerpt));
    }

    private static String stillingskode(JsonNode node, String excerpt) {
        String value = digits(node);
        if (value == null) {
            return null;
        }
        if (!STILLINGSKODE.matcher(value).matches() || !containsNumber(excerpt, value)) {
            return skipped("stillingskode");
        }
        return value;
    }

    private static Integer lonnstrinn(JsonNode node, String excerpt) {
        return boundedInt("lonnstrinn", node, excerpt);
    }

    private static Integer positionPercent(JsonNode node, String excerpt) {
        return boundedInt("positionPercent", node, excerpt);
    }

    /** A whole number from 1 to 100 that is written in the excerpt. */
    private static Integer boundedInt(String field, JsonNode node, String excerpt) {
        String value = digits(node);
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d{1,3}") || !containsNumber(excerpt, value)) {
            return skipped(field);
        }
        int number = Integer.parseInt(value);
        return number >= 1 && number <= 100 ? Integer.valueOf(number) : skipped(field);
    }

    private static BigDecimal annualSalaryFullTime(JsonNode node, String excerpt) {
        String value = digits(node);
        if (value == null) {
            return null;
        }
        if (!value.matches("\\d{1,7}")) {
            return skipped("annualSalaryFullTime");
        }
        long amount = Long.parseLong(value);
        if (amount < 1 || amount > MAX_ANNUAL_SALARY || !containsAmount(excerpt, amount)) {
            return skipped("annualSalaryFullTime");
        }
        return BigDecimal.valueOf(amount);
    }

    private static YearMonth ansiennitetsdato(JsonNode node, String excerpt) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual() || !YEAR_MONTH.matcher(node.asText()).matches()) {
            return skipped("ansiennitetsdato");
        }
        YearMonth date;
        try {
            date = YearMonth.parse(node.asText());
        } catch (Exception e) {
            return skipped("ansiennitetsdato");
        }
        if (date.isBefore(EARLIEST) || date.isAfter(YearMonth.now()) || !containsDate(excerpt, date)) {
            return skipped("ansiennitetsdato");
        }
        return date;
    }

    /** Employer name or job title: form-safe characters, within the form's length, and written in the excerpt. */
    private static String freeText(String field, JsonNode node, int maxChars, String excerpt) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            return skipped(field);
        }
        String value = node.asText().replaceAll("\\s+", " ").strip();
        if (value.length() < 2 || value.length() > maxChars || !FORM_TEXT.matcher(value).matches()
                || !normalised(excerpt).contains(normalised(value))) {
            return skipped(field);
        }
        return value;
    }

    /** A number or digit string as text, or null when the model said null or gave nothing. */
    private static String digits(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asText();
        }
        return node.isTextual() ? node.asText().strip() : "?";
    }

    private static String normalised(String text) {
        return text.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static boolean containsNumber(String excerpt, String number) {
        return Pattern.compile("(?<!\\d)" + Pattern.quote(number) + "(?!\\d)").matcher(excerpt).find();
    }

    /** True when some amount in the excerpt has this whole-NOK part, however its thousands are separated. */
    private static boolean containsAmount(String excerpt, long amount) {
        Matcher matcher = AMOUNT.matcher(excerpt);
        while (matcher.find()) {
            String whole = matcher.group(1).replaceAll("[ .\\u00a0]", "");
            if (whole.length() <= 12 && Long.parseLong(whole) == amount) {
                return true;
            }
        }
        return false;
    }

    /** The month and year must be written together in the excerpt, as 08.2015, 1.8.2015, 01/08/2015 or 2015-08. */
    private static boolean containsDate(String excerpt, YearMonth date) {
        int month = date.getMonthValue();
        int year = date.getYear();
        return Pattern.compile("(?<!\\d)0?" + month + "[./ -]" + year + "(?!\\d)"
                + "|(?<!\\d)" + year + "[-./]0?" + month + "(?!\\d)").matcher(excerpt).find();
    }

    private static <T> T skipped(String field) {
        log.warn("Payslip value skipped: {} failed a check", field);
        return null;
    }

    private static PayslipFields rejected(String check) {
        log.warn("Payslip extraction rejected: {}", check);
        return PayslipFields.empty();
    }
}
