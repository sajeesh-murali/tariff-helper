package com.tariffhelper.service;

import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.api.ApiExceptions.AgreementNotAvailableException;
import com.tariffhelper.api.ApiExceptions.AgreementNotFoundException;
import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import com.tariffhelper.api.EstimateRequest;
import com.tariffhelper.api.EstimateResponse;
import com.tariffhelper.api.ExperienceDto;
import com.tariffhelper.domain.Agreement;
import com.tariffhelper.domain.EstimateFacts;
import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.JobCategory;
import com.tariffhelper.domain.PrivateServiceRule;
import com.tariffhelper.domain.Rules;
import com.tariffhelper.domain.SalaryRange;
import com.tariffhelper.domain.SalaryStep;
import com.tariffhelper.domain.SeniorityResult;
import com.tariffhelper.llm.LlmAssessment;
import com.tariffhelper.llm.LlmClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * The numbers (seniority, pay step, difference) are calculated here, in code.
 * The LLM is only asked to explain them.
 */
@Service
public class EstimateService {

    static final String DISCLAIMER = "This is an estimate, not legal or financial advice. "
            + "Your employer makes the final decision on seniority and salary.";

    private static final YearMonth EARLIEST_START = YearMonth.of(1950, 1);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AgreementRepository agreements;
    private final SeniorityCalculator calculator;
    private final SalaryEstimator salaryEstimator;
    private final LlmClient llm;

    public EstimateService(AgreementRepository agreements, SeniorityCalculator calculator,
                           SalaryEstimator salaryEstimator, LlmClient llm) {
        this.agreements = agreements;
        this.calculator = calculator;
        this.salaryEstimator = salaryEstimator;
        this.llm = llm;
    }

    public EstimateResponse estimate(EstimateRequest request) {
        Agreement agreement = agreements.findById(request.agreementId())
                .orElseThrow(() -> new AgreementNotFoundException(request.agreementId()));
        if (!agreement.hasRules()) {
            throw new AgreementNotAvailableException(agreement.name(), agreement.unavailableReason());
        }
        JobCategory job = agreement.findJobCategory(request.jobCategory())
                .orElseThrow(() -> new InvalidRequestException("jobCategory is not valid for this agreement"));
        validateExperiences(request, job, agreement.rules());

        SeniorityResult seniority = calculator.calculate(request.experiences(), job, agreement.rules());

        List<SalaryStep> ladder = agreement.salaryLadders().get(job.salaryGroup());
        if (ladder == null) {
            throw new IllegalStateException("No salary ladder for the job category");
        }
        SalaryRange minimum = salaryEstimator.rangeFor(ladder, seniority);

        // Pay is compared at 100%: a 50% position earns half of the full-time figure.
        BigDecimal currentFullTime = request.currentAnnualSalary()
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(request.positionPercent()), 0, RoundingMode.HALF_UP);
        SalaryRange difference = new SalaryRange(
                minimum.min().subtract(currentFullTime),
                minimum.max().subtract(currentFullTime));

        EstimateFacts facts = new EstimateFacts(agreement, job, seniority, currentFullTime, minimum, difference);
        LlmAssessment assessment = llm.assess(request, facts);

        List<String> caveats = new ArrayList<>();
        if (job.note() != null && !job.note().isBlank()) {
            caveats.add(job.note().trim());
        }
        caveats.addAll(agreement.caveats());

        return new EstimateResponse(
                agreement.name(),
                agreement.effectiveFrom(),
                job.label(),
                job.salaryGroup(),
                currentFullTime,
                seniority.countedYears(),
                seniority.countedYearsIfAllAccepted(),
                minimum,
                difference,
                seniority.perExperience(),
                assessment.explanation(),
                caveats,
                DISCLAIMER);
    }

    /** Messages name the entry number only, never the submitted values. */
    private void validateExperiences(EstimateRequest request, JobCategory job, Rules rules) {
        YearMonth now = YearMonth.now();
        List<ExperienceDto> list = request.experiences();
        for (int i = 0; i < list.size(); i++) {
            ExperienceDto e = list.get(i);
            if (e.startDate().isBefore(EARLIEST_START) || e.endDate().isAfter(now)) {
                throw new InvalidRequestException(
                        "experiences[" + i + "]: dates must be between 1950-01 and the current month");
            }
            if (rules.privateService() == PrivateServiceRule.BY_EDUCATION_REQUIREMENT
                    && job.requiresEducation() && e.kind() == ExperienceKind.PRIVATE_SERVICE
                    && e.relevance() == null) {
                throw new InvalidRequestException("experiences[" + i + "].relevance is required for private "
                        + "service when the position requires education");
            }
        }
    }
}
