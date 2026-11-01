package com.tariffhelper.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for reading pictures, bound from {@code app.ocr}.
 *
 * @param enabled        false turns image reading off (a clear message is shown instead)
 * @param command        the Tesseract program; it must be installed on the server
 * @param heicCommand    the ImageMagick program (with HEIF support) that turns HEIC/HEIF pictures into PNG
 * @param languages      Tesseract language packs, for example {@code nor+eng}
 * @param timeoutSeconds how long one picture may take
 * @param maxSide        the longest side, in pixels, a picture is scaled to before reading; about 3000 gives
 *                       300 DPI on a full A4 page, but needs more memory
 */
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("tesseract") String command,
        @DefaultValue("magick") String heicCommand,
        @DefaultValue("nor+eng") String languages,
        @DefaultValue("60") int timeoutSeconds,
        @DefaultValue("3000") int maxSide) {
}
