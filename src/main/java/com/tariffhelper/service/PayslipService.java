package com.tariffhelper.service;

import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.api.dto.response.SuggestResponse;
import com.tariffhelper.domain.Agreement;
import com.tariffhelper.domain.PayslipFields;
import com.tariffhelper.llm.PayslipFieldExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Payslip PDF to form values and a suggested agreement: read the text with PDFBox, cut it down and mask personal
 * numbers, let the model copy out the values, then pick the agreement from the employer name (the rules in code
 * first, the model only if the rules cannot decide). The PDF is never stored. Only the small redacted excerpt, and
 * later the employer name and job title, can reach the model.
 */
@Service
public class PayslipService {

    private static final Logger log = LoggerFactory.getLogger(PayslipService.class);

    /** @param suggestion the suggested agreement, or {@link SuggestResponse#none()}; agreement is null when none */
    public record Result(PayslipFields fields, SuggestResponse suggestion, Agreement agreement) {
    }

    private final PayslipTextReader reader;
    private final PayslipRedactor redactor;
    private final PayslipFieldExtractor extractor;
    private final AgreementSuggestionService suggestions;
    private final AgreementRepository agreements;

    public PayslipService(PayslipTextReader reader, PayslipRedactor redactor, PayslipFieldExtractor extractor,
                          AgreementSuggestionService suggestions, AgreementRepository agreements) {
        this.reader = reader;
        this.redactor = redactor;
        this.extractor = extractor;
        this.suggestions = suggestions;
        this.agreements = agreements;
    }

    public Result parse(byte[] pdf) {
        String excerpt = redactor.excerpt(reader.read(pdf));
        if (excerpt.isBlank()) {
            log.info("Payslip has no line with the expected labels, nothing sent to the model");
            return new Result(PayslipFields.empty(), SuggestResponse.none(), null);
        }
        PayslipFields fields = extractor.extract(excerpt);
        if (fields.employerName() == null) {
            return new Result(fields, SuggestResponse.none(), null);
        }
        SuggestResponse suggestion = suggestions.suggest(fields.employerName(), fields.jobTitle());
        Agreement agreement = suggestion.suggestions().stream().findFirst()
                .flatMap(top -> agreements.findById(top.agreementId())).orElse(null);
        return new Result(fields, suggestion, agreement);
    }
}
