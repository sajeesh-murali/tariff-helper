package com.tariffhelper.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PayslipPropertiesTest {

    @Autowired
    PayslipProperties properties;

    @Test
    void theModelReadsEveryPayslipAsASecondOpinionUnlessTheSettingSaysOtherwise() {
        assertThat(properties.aiMode()).isEqualTo(PayslipProperties.AiMode.ALWAYS);
    }
}
