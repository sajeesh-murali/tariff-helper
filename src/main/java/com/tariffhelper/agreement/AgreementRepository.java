package com.tariffhelper.agreement;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.tariffhelper.domain.Agreement;
import com.tariffhelper.domain.JobCategory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Loads the agreement rules files from classpath:agreements/*.yaml once at startup. */
@Component
public class AgreementRepository {

    private final List<Agreement> agreements;

    public AgreementRepository() {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:agreements/*.yaml");
            List<Agreement> loaded = new java.util.ArrayList<>();
            for (Resource file : files) {
                try (InputStream in = file.getInputStream()) {
                    loaded.add(yaml.readValue(in, Agreement.class));
                }
            }
            loaded.forEach(AgreementRepository::validate);
            loaded.sort(Comparator.comparing(Agreement::id));
            this.agreements = List.copyOf(loaded);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load agreement files", e);
        }
    }

    /**
     * Fail at startup if a rules file is inconsistent, rather than give wrong estimates later.
     * Placeholder agreements (no rules) are allowed.
     */
    static void validate(Agreement a) {
        if (a.id() == null || a.id().isBlank() || a.name() == null) {
            throw new IllegalStateException("An agreement file is missing id or name");
        }
        if (!a.hasRules()) {
            return;
        }
        for (JobCategory c : a.jobCategories()) {
            if (c.id() == null || c.label() == null || c.educationRequired() == null || c.salaryGroup() == null) {
                throw new IllegalStateException("Agreement " + a.id()
                        + ": every job category needs id, label, educationRequired and salaryGroup");
            }
            if (!a.salaryLadders().containsKey(c.salaryGroup())) {
                throw new IllegalStateException("Agreement " + a.id() + ": job category " + c.id()
                        + " refers to a salary group with no ladder");
            }
        }
        a.salaryLadders().forEach((group, steps) -> {
            boolean startsAtZero = steps != null && steps.stream().anyMatch(s -> s.fromYears() == 0);
            if (!startsAtZero) {
                throw new IllegalStateException("Agreement " + a.id() + ": salary ladder " + group
                        + " needs a step from 0 years");
            }
        });
        if (a.rules().careWorkMaxYears() <= 0 || a.rules().homeWorkMaxYears() <= 0) {
            throw new IllegalStateException("Agreement " + a.id() + ": rules need careWorkMaxYears and homeWorkMaxYears");
        }
    }

    public List<Agreement> findAll() {
        return agreements;
    }

    public Optional<Agreement> findById(String id) {
        return agreements.stream().filter(a -> a.id().equals(id)).findFirst();
    }
}
