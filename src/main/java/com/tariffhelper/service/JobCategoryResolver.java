package com.tariffhelper.service;

import com.tariffhelper.domain.Agreement;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Derives a job category from a job title or an Oslo stillingskode. Only ever returns a category id
 * that exists in the given agreement.
 */
@Component
public class JobCategoryResolver {

    // Longest keyword first, so "spesialsykepleier" wins over "sykepleier" and "barnehageassistent" over "assistent".
    private static final List<Map.Entry<String, String>> TITLE_KEYWORDS = List.of(
            Map.entry("barnehageassistent", "kindergarten_assistant"),
            Map.entry("spesialsykepleier", "specialist_nurse"),
            Map.entry("helsefagarbeider", "health_care_worker"),
            Map.entry("barnehagelærer", "kindergarten_teacher"),
            Map.entry("pleieassistent", "care_assistant"),
            Map.entry("sykepleier", "nurse"),
            Map.entry("assistent", "care_assistant"),
            Map.entry("nurse", "nurse"));

    // Oslo kommune stillingskoder, as named in the job category labels of oslo-kommune.yaml.
    private static final Map<String, Map<String, String>> STILLINGSKODER = Map.of(
            "oslo-kommune", Map.of(
                    "58", "nurse",
                    "59", "specialist_nurse",
                    "60", "specialist_nurse",
                    "61", "specialist_nurse",
                    "94", "clinical_nurse"));

    public Optional<String> fromJobTitle(Agreement agreement, String jobTitle) {
        if (jobTitle == null || jobTitle.isBlank()) {
            return Optional.empty();
        }
        String title = AgreementMatcher.normalise(jobTitle);
        return TITLE_KEYWORDS.stream()
                .filter(e -> title.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .filter(id -> agreement.findJobCategory(id).isPresent());
    }

    public Optional<String> fromStillingskode(Agreement agreement, String stillingskode) {
        if (stillingskode == null || stillingskode.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(STILLINGSKODER.getOrDefault(agreement.id(), Map.of()).get(stillingskode.strip()))
                .filter(id -> agreement.findJobCategory(id).isPresent());
    }
}
