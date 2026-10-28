package com.tariffhelper.service;

import com.tariffhelper.api.dto.request.ExperienceDto;
import com.tariffhelper.api.dto.request.ScreeningDto;
import com.tariffhelper.api.dto.request.ScreeningDto.WorkAnswer;
import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.JobCategory;
import com.tariffhelper.domain.PerExperience;
import com.tariffhelper.domain.PeriodOverlap;
import com.tariffhelper.domain.Rules;
import com.tariffhelper.domain.SeniorityResult;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static com.tariffhelper.api.dto.request.ScreeningDto.months;

/**
 * Adds the questionnaire answers (total time, no dates) to a seniority result. No agreement rule is added here: each
 * answer becomes a period of the right kind and goes through {@link SeniorityCalculator}, so every provision and
 * limit comes from the same code and YAML files as for dated periods.
 *
 * Totals have no dates, so overlap cannot be removed exactly. The answer to "did any periods happen at the same time?"
 * decides:
 * - NO: the periods are placed one after the other, so the months add up.
 * - YES or UNSURE (also when it is missing and there is more than one period): the result is a range. The low end
 *   places every period at the same time, so the months only overlap as far as they must; the high end is the
 *   sum. The low end is what is certain; the rest counts as "only if the employer accepts it".
 *
 * Work in Norway or abroad is treated as private service, because the questions do not ask whether it was public or
 * private. For positions that need an education in KS this gives an uncertain result, as for private work of unknown
 * relevance. Au pair earns nothing. Military service abroad is treated like service in Norway; the result says that the
 * rules do not name the country.
 */
@Component
public class ScreeningSeniorityCalculator {

    /** Far from any real date, so these synthetic periods never overlap periods the person entered with dates. */
    private static final YearMonth BASE = YearMonth.of(3000, 1);

    private record Bucket(String label, ExperienceKind kind, String country, int months, boolean approximate,
                          String extraNote) {
    }

    private final SeniorityCalculator calculator;

    public ScreeningSeniorityCalculator(SeniorityCalculator calculator) {
        this.calculator = calculator;
    }

    /** What the confidence level needs to know about the answers. */
    public record Info(boolean used, boolean approximate, boolean overlapUncertain) {
        public static final Info NONE = new Info(false, false, false);
    }

    public Info describe(ScreeningDto screening, boolean datedPresent) {
        List<Bucket> buckets = buckets(screening);
        if (buckets.isEmpty()) {
            return Info.NONE;
        }
        boolean approximate = buckets.stream().anyMatch(Bucket::approximate);
        return new Info(true, approximate, overlapRange(screening, buckets.size(), datedPresent));
    }

    /**
     * @param dated      the result for the periods with dates (empty result when there are none)
     * @param datedCount how many dated periods there are; summary entries are numbered after them
     */
    public SeniorityResult combine(SeniorityResult dated, int datedCount, ScreeningDto screening,
                                   JobCategory job, Rules rules) {
        List<Bucket> buckets = buckets(screening);
        if (buckets.isEmpty()) {
            return dated;
        }
        boolean datedPresent = datedCount > 0;
        boolean range = overlapRange(screening, buckets.size(), datedPresent);

        List<ExperienceDto> sequential = new ArrayList<>();
        List<ExperienceDto> together = new ArrayList<>();
        YearMonth next = BASE;
        for (Bucket b : buckets) {
            sequential.add(new ExperienceDto(b.country(), b.kind(), null, null, next,
                    next.plusMonths(b.months() - 1L), null));
            together.add(new ExperienceDto(b.country(), b.kind(), null, null, BASE,
                    BASE.plusMonths(b.months() - 1L), null));
            next = next.plusMonths(b.months());
        }
        SeniorityResult high = calculator.calculate(sequential, job, rules);
        SeniorityResult low = range ? calculator.calculate(together, job, rules) : high;

        List<PerExperience> entries = new ArrayList<>(dated.perExperience());
        int summaryCounted = 0;
        int summaryPossible = 0;
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            PerExperience sum = high.perExperience().get(i);
            int counted = low.perExperience().get(i).countedMonths();
            int possible = range
                    ? Math.max(0, sum.countedMonths() + sum.possibleExtraMonths() - counted)
                    : sum.possibleExtraMonths();
            entries.add(new PerExperience(datedCount + i, counted, possible, note(b, sum.note(), range)));
            summaryCounted += counted;
            summaryPossible += possible;
        }

        int counted;
        int possible;
        if (!datedPresent) {
            counted = summaryCounted;
            possible = summaryPossible;
        } else if (!range) {
            counted = dated.countedMonths() + summaryCounted;
            possible = dated.possibleExtraMonths() + summaryPossible;
        } else {
            // The dated periods count as one more block that may overlap the summary answers.
            counted = Math.max(dated.countedMonths(), summaryCounted);
            int upper = dated.countedMonths() + dated.possibleExtraMonths() + summaryCounted + summaryPossible;
            possible = upper - counted;
        }
        return new SeniorityResult(counted, possible, List.copyOf(entries));
    }

    private static boolean overlapRange(ScreeningDto screening, int bucketCount, boolean datedPresent) {
        if (bucketCount + (datedPresent ? 1 : 0) < 2) {
            return false;
        }
        PeriodOverlap overlap = screening.periodsOverlap() == null ? PeriodOverlap.UNSURE : screening.periodsOverlap();
        return overlap != PeriodOverlap.NO;
    }

    private static String note(Bucket b, String calculatorNote, boolean range) {
        StringBuilder text = new StringBuilder(b.label()).append(" (summary answer, no dates): ").append(calculatorNote);
        if (b.extraNote() != null) {
            text.append(' ').append(b.extraNote());
        }
        if (b.approximate()) {
            text.append(" You marked this answer as approximate.");
        }
        if (range) {
            text.append(" Some periods may have overlapped, so the months for the summary answers are a range.");
        }
        return text.toString();
    }

    private static List<Bucket> buckets(ScreeningDto s) {
        List<Bucket> list = new ArrayList<>();
        if (s == null) {
            return list;
        }
        work(list, s.workInNorway(), "Work in Norway", "NO");
        work(list, s.workAbroad(), "Work outside Norway", "XX");
        if (s.auPair() != null && months(s.auPair().duration()) > 0) {
            list.add(new Bucket("Au pair", ExperienceKind.AU_PAIR, "XX", months(s.auPair().duration()),
                    Boolean.TRUE.equals(s.auPair().approximate()), null));
        }
        if (s.militaryService() != null && months(s.militaryService().duration()) > 0) {
            String country = s.militaryService().country();
            boolean abroad = country != null && !country.equalsIgnoreCase("NO");
            list.add(new Bucket("Military or civil service", ExperienceKind.MILITARY_SERVICE,
                    country == null ? "NO" : country.toUpperCase(), months(s.militaryService().duration()),
                    Boolean.TRUE.equals(s.militaryService().approximate()),
                    abroad ? "The rules we found do not say whether service abroad counts the same way; "
                            + "the employer decides." : null));
        }
        if (s.childCare() != null && months(s.childCare().duration()) > 0) {
            list.add(new Bucket("Care for your own children at home", ExperienceKind.CARE_WORK, "NO",
                    months(s.childCare().duration()), Boolean.TRUE.equals(s.childCare().approximate()), null));
        }
        if (s.otherHome() != null && months(s.otherHome().duration()) > 0) {
            ExperienceKind type = s.otherHome().type();
            list.add(new Bucket(type == ExperienceKind.CARE_WORK ? "Other care at home" : "Other work in the home",
                    type, "NO", months(s.otherHome().duration()),
                    Boolean.TRUE.equals(s.otherHome().approximate()), null));
        }
        return list;
    }

    private static void work(List<Bucket> list, WorkAnswer answer, String label, String country) {
        if (answer == null || months(answer.duration()) == 0) {
            return;
        }
        int total = months(answer.duration());
        int own = Math.min(months(answer.selfEmployed()), total);
        boolean approximate = Boolean.TRUE.equals(answer.approximate());
        if (total - own > 0) {
            list.add(new Bucket(label, ExperienceKind.PRIVATE_SERVICE, country, total - own, approximate,
                    "We did not ask whether it was public or private work, so it is treated as private service."));
        }
        if (own > 0) {
            list.add(new Bucket(label + ", own firm", ExperienceKind.SELF_EMPLOYED, country, own, approximate, null));
        }
    }
}
