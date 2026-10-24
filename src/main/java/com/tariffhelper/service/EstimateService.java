package com.tariffhelper.service;

import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.api.ApiExceptions.AgreementNotAvailableException;
import com.tariffhelper.api.ApiExceptions.AgreementNotDeterminedException;
import com.tariffhelper.api.ApiExceptions.AgreementNotFoundException;
import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import com.tariffhelper.api.dto.EstimateRequest;
import com.tariffhelper.api.dto.EstimateResponse;
import com.tariffhelper.api.dto.EstimateResponse.AgreementSuggestionInfo;
import com.tariffhelper.api.dto.EstimateResponse.Confidence;
import com.tariffhelper.api.dto.EstimateResponse.CreditedVsCounted;
import com.tariffhelper.api.dto.ExperienceDto;
import com.tariffhelper.api.dto.SuggestResponse;
import com.tariffhelper.domain.Agreement;
import com.tariffhelper.domain.CreditedSeniority;
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
import java.util.Optional;

/**
 * The numbers (seniority, pay step, difference, credited years, confidence) are calculated here, in code.
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
    private final AgreementSuggestionService suggestions;
    private final JobCategoryResolver jobCategories;
    private final CreditedSeniorityCalculator creditedCalculator;
    private final ConfidenceAssessor confidenceAssessor;

    public EstimateService(AgreementRepository agreements, SeniorityCalculator calculator,
                           SalaryEstimator salaryEstimator, LlmClient llm, AgreementSuggestionService suggestions,
                           JobCategoryResolver jobCategories, CreditedSeniorityCalculator creditedCalculator,
                           ConfidenceAssessor confidenceAssessor) {
        this.agreements = agreements;
        this.calculator = calculator;
        this.salaryEstimator = salaryEstimator;
        this.llm = llm;
        this.suggestions = suggestions;
        this.jobCategories = jobCategories;
        this.creditedCalculator = creditedCalculator;
        this.confidenceAssessor = confidenceAssessor;
    }

    private record AgreementChoice(Agreement agreement, AgreementSuggestionInfo suggestion) {
    }

    private record JobChoice(JobCategory job, JobCategoryBasis basis) {
    }

    public EstimateResponse estimate(EstimateRequest request) {
        validatePayslipDate(request);
        AgreementChoice choice = chooseAgreement(request);
        Agreement agreement = choice.agreement();
        if (!agreement.hasRules()) {
            throw new AgreementNotAvailableException(agreement.name(), agreement.unavailableReason());
        }
        JobChoice jobChoice = chooseJobCategory(request, agreement);
        JobCategory job = jobChoice.job();
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

        CreditedSeniority credited = creditedCalculator.calculate(
                ladder, request.lonnstrinn(), request.ansiennitetsdato(), YearMonth.now());
        Confidence confidence = confidenceAssessor.assess(choice.suggestion(), seniority, jobChoice.basis(), credited);
        CreditedVsCounted creditedVsCounted = credited.compared()
                ? new CreditedVsCounted(credited.creditedYears(), seniority.countedYears(), credited.basis())
                : null;

        EstimateFacts facts = new EstimateFacts(agreement, job, seniority, currentFullTime, minimum, difference,
                payslipConclusion(credited, seniority));
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
                DISCLAIMER,
                choice.suggestion(),
                confidence,
                creditedVsCounted);
    }

    /** The agreement given, or else the one suggested from the employer name (shown as assumed). */
    private AgreementChoice chooseAgreement(EstimateRequest request) {
        if (!isBlank(request.agreementId())) {
            Agreement agreement = agreements.findById(request.agreementId())
                    .orElseThrow(() -> new AgreementNotFoundException(request.agreementId()));
            return new AgreementChoice(agreement, null);
        }
        if (isBlank(request.employerName())) {
            throw new InvalidRequestException("agreementId or employerName is required");
        }
        SuggestResponse response = suggestions.suggest(request.employerName(), request.jobTitle());
        Optional<Agreement> suggested = response.suggestions().stream().findFirst()
                .flatMap(s -> agreements.findById(s.agreementId()));
        if (suggested.isEmpty()) {
            throw new AgreementNotDeterminedException(availableAgreementIds());
        }
        SuggestResponse.Suggestion top = response.suggestions().get(0);
        return new AgreementChoice(suggested.get(),
                new AgreementSuggestionInfo(top.agreementId(), top.confidence(), response.source(), true));
    }

    /** The job category given, or else derived from the stillingskode, then from the job title. */
    private JobChoice chooseJobCategory(EstimateRequest request, Agreement agreement) {
        if (!isBlank(request.jobCategory())) {
            JobCategory job = agreement.findJobCategory(request.jobCategory())
                    .orElseThrow(() -> new InvalidRequestException("jobCategory is not valid for this agreement. "
                            + "Options: " + categoryIds(agreement)));
            return new JobChoice(job, JobCategoryBasis.GIVEN);
        }
        Optional<String> byCode = jobCategories.fromStillingskode(agreement, request.stillingskode());
        if (byCode.isPresent()) {
            return new JobChoice(agreement.findJobCategory(byCode.get()).orElseThrow(), JobCategoryBasis.STILLINGSKODE);
        }
        Optional<String> byTitle = jobCategories.fromJobTitle(agreement, request.jobTitle());
        if (byTitle.isPresent()) {
            return new JobChoice(agreement.findJobCategory(byTitle.get()).orElseThrow(), JobCategoryBasis.JOB_TITLE);
        }
        throw new InvalidRequestException("jobCategory is required and could not be derived from stillingskode "
                + "or jobTitle. Options for this agreement: " + categoryIds(agreement));
    }

    /** In words for the LLM prompt: the conclusion only, never the lønnstrinn or date themselves. */
    private static String payslipConclusion(CreditedSeniority credited, SeniorityResult seniority) {
        if (credited.stepNotOnLadder()) {
            return "The pay step on the payslip is not on the pay ladder for this position, so it could not be "
                    + "compared with the counted years.";
        }
        if (!credited.compared()) {
            return null;
        }
        return "The employer currently credits about " + aboutYears(credited.creditedYears())
                + "; the rules count about " + aboutYears(seniority.countedYears()) + ".";
    }

    private static String aboutYears(BigDecimal years) {
        int whole = years.setScale(0, RoundingMode.HALF_UP).intValue();
        return whole + (whole == 1 ? " year" : " years");
    }

    private List<String> availableAgreementIds() {
        return agreements.findAll().stream().filter(Agreement::hasRules).map(Agreement::id).toList();
    }

    private static String categoryIds(Agreement agreement) {
        return String.join(", ", agreement.jobCategories().stream().map(JobCategory::id).toList());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Messages name the field only, never the submitted value. */
    private static void validatePayslipDate(EstimateRequest request) {
        YearMonth date = request.ansiennitetsdato();
        if (date != null && (date.isBefore(EARLIEST_START) || date.isAfter(YearMonth.now()))) {
            throw new InvalidRequestException("ansiennitetsdato must be between 1950-01 and the current month");
        }
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
