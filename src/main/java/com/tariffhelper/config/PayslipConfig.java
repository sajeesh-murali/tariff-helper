package com.tariffhelper.config;

import com.tariffhelper.service.PayslipProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Makes the {@code app.payslip} settings available to the payslip service. */
@Configuration
@EnableConfigurationProperties(PayslipProperties.class)
public class PayslipConfig {
}
