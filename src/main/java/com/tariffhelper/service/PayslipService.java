package com.tariffhelper.service;

import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.api.dto.response.SuggestResponse;
import com.tariffhelper.domain.Agreement;
import com.tariffhelper.domain.PayslipFields;
import com.tariffhelper.llm.PayslipFieldExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Payslip (PDF or picture) to form values and a suggested agreement: read the text (the PDF text layer, or OCR on
 * this server for pictures and scans), let the rules in code read the values from the whole text, let the model read
 * the small redacted excerpt as a second opinion, compare the two, then pick the agreement from the employer name
 * (the rules in code first, the model only if the rules cannot decide).
 * The file is never stored. Only the small redacted excerpt, and later the employer name and job title, can reach
 * the model; the picture itself never leaves this server.
 */
@Service
public class PayslipService {

    private static final Logger log = LoggerFactory.getLogger(PayslipService.class);

    /** Both readings found the same value. */
    public static final String CONFIRMED = "confirmed";
    /** Both readings found a value and they differ; the rules' value is shown and the person is asked to check. */
    public static final String DISAGREE = "disagree";

    /**
     * @param suggestion  the suggested agreement, or {@link SuggestResponse#none()}; agreement is null when none
     * @param fieldStatus for each form value: "confirmed" (rules and model agree), "disagree" (they differ; the rules'
     *                    value is used), "rules" (only the rules found it), "ai" (only the model found it, and the
     *                    value was checked), "notPrinted" (the payslip leaves it out) or "notFound"
     */
    public record Result(PayslipFields fields, SuggestResponse suggestion, Agreement agreement,
                         Map<String, String> fieldStatus) {
    }

    private final PayslipFileReader reader;
    private final PayslipRuleReader ruleReader;
    private final PayslipRedactor redactor;
    private final PayslipFieldExtractor extractor;
    private final AgreementSuggestionService suggestions;
    private final AgreementRepository agreements;
    private final PayslipProperties.AiMode aiMode;

    public PayslipService(PayslipFileReader reader, PayslipRuleReader ruleReader, PayslipRedactor redactor,
                          PayslipFieldExtractor extractor, AgreementSuggestionService suggestions,
                          AgreementRepository agreements, PayslipProperties properties) {
        this.reader = reader;
        this.ruleReader = ruleReader;
        this.redactor = redactor;
        this.extractor = extractor;
        this.suggestions = suggestions;
        this.agreements = agreements;
        this.aiMode = properties.aiMode();
    }

    public Result parse(byte[] file) {
        return parse(file, null);
    }

    /** @param password for a password-protected PDF, or null; used only to open the file, never stored or logged */
    public Result parse(byte[] file, String password) {
        String text = reader.read(file, password);
        // 1. The rules in code read the whole text on this server; nothing leaves it.
        PayslipRuleReader.Reading reading = ruleReader.read(text);
        PayslipFields fromRules = reading.fields();
        // 2. The model reads only the small redacted excerpt: always, or only when the rules missed something.
        String excerpt = redactor.excerpt(text, reading.usedLines());
        boolean ask = !excerpt.isBlank() && switch (aiMode) {
            case ALWAYS -> true;
            case MISSING -> fromRules.foundCount() < PayslipFields.FIELD_NAMES.size();
            case OFF -> false;
        };
        PayslipFields answer = ask ? extractor.extract(excerpt) : null;
        PayslipFields fromModel = answer == null ? PayslipFields.empty() : answer;
        // 3. Compare the two readings. A value the rules found is never replaced; the model fills the gaps.
        PayslipFields fields = fromRules.fillGapsFrom(fromModel);
        Map<String, String> status = compare(fromRules, fromModel, reading.notPrinted());
        log.info("Payslip text read: textChars={}, excerptLines={}, foundByRules={}, foundInTotal={}, modelAsked={}, "
                        + "confirmed={}, disagree={}", text.length(), excerpt.isBlank() ? 0 : excerpt.lines().count(),
                fromRules.foundCount(), fields.foundCount(), ask, count(status, CONFIRMED), count(status, DISAGREE));
        if (fields.employerName() == null) {
            return new Result(fields, SuggestResponse.none(), null, status);
        }
        SuggestResponse suggestion = suggestions.suggest(fields.employerName(), fields.jobTitle());
        Agreement agreement = suggestion.suggestions().stream().findFirst()
                .flatMap(top -> agreements.findById(top.agreementId())).orElse(null);
        return new Result(fields, suggestion, agreement, status);
    }

    private static Map<String, String> compare(PayslipFields byRules, PayslipFields byModel,
                                               Map<String, String> notPrinted) {
        Map<String, String> status = new LinkedHashMap<>();
        for (String field : PayslipFields.FIELD_NAMES) {
            boolean rules = byRules.has(field);
            boolean model = byModel.has(field);
            if (rules && model) {
                status.put(field, same(byRules.valueOf(field), byModel.valueOf(field)) ? CONFIRMED : DISAGREE);
            } else if (rules) {
                status.put(field, "rules");
            } else if (model) {
                status.put(field, "ai");
            } else {
                status.put(field, notPrinted.getOrDefault(field, "notFound"));
            }
        }
        return status;
    }

    /** Numbers and dates must be equal; names match when one contains the other once spacing and punctuation go. */
    static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        if (a instanceof String x && b instanceof String y) {
            if (x.matches("\\d+") && y.matches("\\d+")) {
                return x.equals(y); // a code is the same code or it is not
            }
            String left = squeeze(x);
            String right = squeeze(y);
            return !left.isEmpty() && !right.isEmpty() && (left.contains(right) || right.contains(left));
        }
        return a.equals(b);
    }

    private static String squeeze(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static long count(Map<String, String> status, String value) {
        return status.values().stream().filter(value::equals).count();
    }
}
