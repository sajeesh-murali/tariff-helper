package com.tariffhelper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tariffhelper.domain.PayslipFields;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Forty invented payslips ({@code payslips/synthetic-samples.json}) in different layouts, languages and systems, with
 * the values each one prints. Every value is made up; no real person's payslip is used. A third of them are hard on
 * purpose: when a slip is unclear (two positions with no main one, a start date but no seniority date, a duration
 * instead of a date), the right answer is an empty field, never a guess.
 */
class PayslipRuleReaderSamplesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final PayslipRuleReader reader = new PayslipRuleReader();

    static Stream<JsonNode> samples() throws IOException {
        try (var in = PayslipRuleReaderSamplesTest.class.getResourceAsStream("/payslips/synthetic-samples.json")) {
            List<JsonNode> all = new ArrayList<>();
            JSON.readTree(in).forEach(all::add);
            return all.stream();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static PayslipFields expected(JsonNode e) {
        String date = text(e, "ansiennitetsdato");
        return new PayslipFields(text(e, "stillingskode"),
                e.get("lonnstrinn").isNull() ? null : e.get("lonnstrinn").asInt(),
                date == null ? null : YearMonth.parse(date), text(e, "employerName"), text(e, "jobTitle"),
                e.get("positionPercent").isNull() ? null : e.get("positionPercent").asInt(),
                e.get("annualSalaryFullTime").isNull() ? null : new BigDecimal(e.get("annualSalaryFullTime").asText()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void readsExactlyWhatTheSlipPrints(JsonNode sample) {
        PayslipFields fields = reader.read(sample.get("text").asText()).fields();
        assertThat(fields).as(sample.get("id").asText() + ": " + sample.get("notes").asText())
                .isEqualTo(expected(sample.get("expected")));
    }

    @Test
    void theSampleFileIsComplete() throws IOException {
        assertThat(samples().count()).isGreaterThanOrEqualTo(40);
        assertThat(samples().filter(s -> s.get("hard").asBoolean()).count()).isGreaterThanOrEqualTo(10);
    }
}
