package com.tariffhelper.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for reading payslips, bound from {@code app.payslip}.
 *
 * @param aiMode when the model reads the payslip excerpt as well as the rules in code: {@code always} (a second
 *               opinion on every payslip; the two readings are compared), {@code missing} (only for values the
 *               rules did not find) or {@code off} (never; nothing is sent to a model)
 */
@ConfigurationProperties(prefix = "app.payslip")
public record PayslipProperties(@DefaultValue("always") AiMode aiMode) {

    public enum AiMode { ALWAYS, MISSING, OFF }
}
