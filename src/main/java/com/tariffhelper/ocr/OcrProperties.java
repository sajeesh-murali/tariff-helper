package com.tariffhelper.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for reading pictures, bound from {@code app.ocr}.
 *
 * @param enabled        false turns image reading off (a clear message is shown instead)
 * @param command        the Tesseract program; it must be installed on the server
 * @param languages      Tesseract language packs, for example {@code nor+eng}
 * @param timeoutSeconds how long one picture may take
 */
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("tesseract") String command,
        @DefaultValue("nor+eng") String languages,
        @DefaultValue("40") int timeoutSeconds) {
}
