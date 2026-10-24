package com.tariffhelper.service;

import com.tariffhelper.api.dto.ExperienceDto;
import com.tariffhelper.domain.ExperienceKind;
import com.tariffhelper.domain.HomeWorkRule;
import com.tariffhelper.domain.JobCategory;
import com.tariffhelper.domain.PerExperience;
import com.tariffhelper.domain.PrivateServiceRule;
import com.tariffhelper.domain.Relevance;
import com.tariffhelper.domain.Rules;
import com.tariffhelper.domain.SeniorityResult;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic seniority calculation. The provisions are chosen by the agreement's {@link Rules}:
 * KS Hovedtariffavtale (HTA § 12.1) and Oslo kommune (Dokument 25, lønnsansiennitet) are implemented.
 *
 * Works in whole calendar months. Part-time counts as full time for the period it lasted, so
 * position percentage is deliberately ignored. A month covered by several periods is counted once,
 * under the strongest provision. The result is a range: months that count, plus months that count
 * only if the employer accepts them (private service of partial relevance, self-employment).
 *
 * Not modelled (shown to the user as caveats instead): the age-18 start, hourly-paid work thresholds,
 * and documentation.
 */
@Component
public class SeniorityCalculator {

    /** How a period is credited. When two periods cover the same month, the earlier constant wins. */
    private enum Credit {
        FULL,
        POSSIBLE,
        /** Paid work that earns no credit. It still occupies the month, so care or home work cannot overlap it. */
        PAID_NOT_CREDITED,
        CARE,
        HOME,
        /** Work in the home that may count if the employer accepts it (Oslo). Never certain. */
        HOME_POSSIBLE,
        NOT_CREDITED
    }

    public SeniorityResult calculate(List<ExperienceDto> experiences, JobCategory job, Rules rules) {
        int n = experiences.size();
        Credit[] credit = new Credit[n];
        for (int i = 0; i < n; i++) {
            credit[i] = classify(experiences.get(i), job, rules);
        }

        // Give each calendar month to one period: the strongest provision, earliest entry on a tie.
        Map<YearMonth, Integer> winner = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ExperienceDto e = experiences.get(i);
            for (YearMonth m = e.startDate(); !m.isAfter(e.endDate()); m = m.plusMonths(1)) {
                Integer current = winner.get(m);
                if (current == null || credit[i].ordinal() < credit[current].ordinal()) {
                    winner.put(m, i);
                }
            }
        }
        int[] won = new int[n];
        for (int index : winner.values()) {
            won[index]++;
        }

        int careLeft = rules.careWorkMaxYears() * 12;
        int homeLeft = rules.homeWorkMaxYears() * 12;
        // Uncertain work in the home shares its limit with the care work that is certain to count.
        int careCounted = 0;
        for (int i = 0; i < n; i++) {
            if (credit[i] == Credit.CARE) {
                careCounted += won[i];
            }
        }
        careCounted = Math.min(careCounted, careLeft);
        int homePossibleLeft = Math.max(0, homeLeft - careCounted);
        List<PerExperience> perExperience = new ArrayList<>();
        int countedTotal = 0;
        int possibleTotal = 0;

        for (int i = 0; i < n; i++) {
            int counted = 0;
            int possible = 0;
            switch (credit[i]) {
                case FULL -> counted = won[i];
                case POSSIBLE -> possible = won[i];
                case CARE -> {
                    counted = Math.min(won[i], careLeft);
                    careLeft -= counted;
                }
                case HOME -> {
                    counted = Math.min(won[i], homeLeft);
                    homeLeft -= counted;
                }
                case HOME_POSSIBLE -> {
                    possible = Math.min(won[i], homePossibleLeft);
                    homePossibleLeft -= possible;
                }
                case PAID_NOT_CREDITED, NOT_CREDITED -> {
                    // earns nothing
                }
            }
            int totalMonths = (int) ChronoUnit.MONTHS.between(
                    experiences.get(i).startDate(), experiences.get(i).endDate()) + 1;
            perExperience.add(new PerExperience(i, counted, possible,
                    note(experiences.get(i), job, rules, credit[i], totalMonths, won[i], counted, possible)));
            countedTotal += counted;
            possibleTotal += possible;
        }
        return new SeniorityResult(countedTotal, possibleTotal, List.copyOf(perExperience));
    }

    private static Credit classify(ExperienceDto e, JobCategory job, Rules rules) {
        return switch (e.kind()) {
            case PUBLIC_SERVICE, MILITARY_SERVICE -> Credit.FULL;
            case PRIVATE_SERVICE -> classifyPrivate(e, job, rules);
            case SELF_EMPLOYED -> Credit.POSSIBLE;
            case CARE_WORK -> Credit.CARE;
            case HOME_WORK -> {
                if (rules.homeWork() == HomeWorkRule.POSSIBLE_ONLY) {
                    yield Credit.HOME_POSSIBLE;
                }
                yield job.requiresEducation() ? Credit.NOT_CREDITED : Credit.HOME;
            }
        };
    }

    private static Credit classifyPrivate(ExperienceDto e, JobCategory job, Rules rules) {
        if (rules.privateService() == PrivateServiceRule.ALWAYS_FULL || !job.requiresEducation()) {
            return Credit.FULL;
        }
        Relevance relevance = e.relevance() == null ? Relevance.PARTLY_RELATED : e.relevance();
        return switch (relevance) {
            case RELATED -> Credit.FULL;
            case PARTLY_RELATED -> Credit.POSSIBLE;
            case UNRELATED -> Credit.PAID_NOT_CREDITED;
        };
    }

    private static String note(ExperienceDto e, JobCategory job, Rules rules, Credit credit,
                               int totalMonths, int wonMonths, int countedMonths, int possibleMonths) {
        String text = switch (credit) {
            case FULL -> fullNote(e, job, rules);
            case POSSIBLE -> e.kind() == ExperienceKind.SELF_EMPLOYED
                    ? "Self-employment is assessed by the employer, usually from tax records. "
                    + "It may count fully or only partly."
                    : "Private service counts only if the employer considers it of importance for the "
                    + "position. It may count.";
            case HOME_POSSIBLE -> homePossibleNote(rules, wonMonths, possibleMonths);
            case PAID_NOT_CREDITED -> "Does not count: private service in a position with an education "
                    + "requirement counts only if it is of importance for the position.";
            case CARE -> limitNote("Care work", rules.careWorkMaxYears(), wonMonths, countedMonths);
            case HOME -> limitNote("Work in the home", rules.homeWorkMaxYears(), wonMonths, countedMonths);
            case NOT_CREDITED -> "Does not count: work in the home is credited only for positions "
                    + "without an education requirement.";
        };
        int overlap = totalMonths - wonMonths;
        if (overlap > 0) {
            text += " " + overlap + " month(s) overlap with other periods and are not counted twice.";
        }
        return text;
    }

    private static String homePossibleNote(Rules rules, int wonMonths, int possibleMonths) {
        String text = "Work in the home is not mentioned in the rules. It may be accepted as care work "
                + "(care for children, elderly or sick people), up to " + rules.homeWorkMaxYears()
                + " years in total together with care work that already counts. The employer decides.";
        if (possibleMonths < wonMonths) {
            text += " The limit was reached, so " + (wonMonths - possibleMonths) + " month(s) cannot count.";
        }
        return text;
    }

    private static String fullNote(ExperienceDto e, JobCategory job, Rules rules) {
        if (e.kind() == ExperienceKind.MILITARY_SERVICE) {
            return "Military service counts in full.";
        }
        if (e.kind() == ExperienceKind.PUBLIC_SERVICE) {
            return "Public-sector service counts in full.";
        }
        if (rules.privateService() == PrivateServiceRule.ALWAYS_FULL) {
            return "Private service with fixed agreed working hours counts in full, "
                    + "whether or not it is related to the position.";
        }
        return job.requiresEducation()
                ? "Private service related to the position counts (the employer decides)."
                : "Private service counts in full for positions without an education requirement.";
    }

    private static String limitNote(String what, int maxYears, int wonMonths, int countedMonths) {
        if (countedMonths < wonMonths) {
            return what + " counts up to " + maxYears + " years in total. The limit was reached, so "
                    + (wonMonths - countedMonths) + " month(s) are not counted.";
        }
        return what + " counts, up to " + maxYears + " years in total.";
    }
}
