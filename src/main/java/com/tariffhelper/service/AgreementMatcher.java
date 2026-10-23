package com.tariffhelper.service;

import com.tariffhelper.domain.AgreementMatch;
import com.tariffhelper.domain.ConfidenceLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Deterministic rules from the employer name to an agreement. Runs before the AI is asked.
 * The rules are tried in order: the first match wins (so "universitetssykehus" is Spekter, not the state).
 */
@Component
public class AgreementMatcher {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern HELSE_HF = Pattern.compile("\\bhelse .* hf\\b", FLAGS);
    // Whole word only, so names like "Navestad" do not match.
    private static final Pattern NAV = Pattern.compile("\\bnav\\b", FLAGS);
    // Word start, so "politiet" and "politidistrikt" match but "politisk" does not.
    private static final Pattern POLITI = Pattern.compile("\\bpoliti(?!sk)", FLAGS);

    private record Rule(Predicate<String> test, String agreementId, ConfidenceLevel confidence, String reason) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(n -> n.contains("oslo kommune") || n.startsWith("bydel ")
                    || containsAny(n, "sykehjemsetaten", "velferdsetaten", "helseetaten"),
                    "oslo-kommune", ConfidenceLevel.HIGH,
                    "The employer name is Oslo kommune or one of its districts (bydeler) or agencies (etater)."),
            new Rule(n -> n.endsWith(" kommune"), "ks-hta", ConfidenceLevel.HIGH,
                    "The employer name is a municipality (kommune) outside Oslo, which uses the KS agreement."),
            new Rule(n -> HELSE_HF.matcher(n).find() || n.endsWith(" hf")
                    || containsAny(n, "universitetssykehus", "sykehus"),
                    "spekter", ConfidenceLevel.MEDIUM,
                    "The employer name looks like a hospital or health trust (HF), which usually uses Spekter. "
                            + "Some hospitals use another agreement."),
            new Rule(n -> NAV.matcher(n).find() || POLITI.matcher(n).find()
                    || containsAny(n, "universitet", "forsvar", "kriminalomsorg", "skatteetaten"),
                    "staten", ConfidenceLevel.MEDIUM,
                    "The employer name looks like a state body (for example NAV, the police or a university). "
                            + "Some local offices are run by the municipality instead."));

    public Optional<AgreementMatch> match(String employerName) {
        if (employerName == null) {
            return Optional.empty();
        }
        String name = normalise(employerName);
        return RULES.stream()
                .filter(rule -> rule.test().test(name))
                .findFirst()
                .map(rule -> new AgreementMatch(rule.agreementId(), rule.confidence(), rule.reason()));
    }

    /** Lowercase, trimmed, single spaces. */
    static String normalise(String text) {
        return WHITESPACE.matcher(text.strip().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    private static boolean containsAny(String name, String... words) {
        for (String word : words) {
            if (name.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
