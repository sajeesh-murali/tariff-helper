package com.tariffhelper.config;

import com.tariffhelper.ocr.OcrProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Makes the {@code app.ocr} settings available to the picture reader. */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrConfig {
}
