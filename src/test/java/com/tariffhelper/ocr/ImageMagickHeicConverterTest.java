package com.tariffhelper.ocr;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only the failure paths: they need no ImageMagick installed. Real conversion is checked where it is installed. */
class ImageMagickHeicConverterTest {

    private static final byte[] PICTURE = {1, 2, 3};

    @Test
    void saysSoWhenPictureReadingIsTurnedOff() {
        ImageMagickHeicConverter converter =
                new ImageMagickHeicConverter(new OcrProperties(false, "tesseract", "magick", "nor+eng", 5, 3000));

        assertThatThrownBy(() -> converter.toPng(PICTURE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not available on this server");
    }

    @Test
    void saysSoWhenTheProgramIsNotInstalled() {
        ImageMagickHeicConverter converter = new ImageMagickHeicConverter(
                new OcrProperties(true, "tesseract", "no-such-heic-program-xyz", "nor+eng", 5, 3000));

        assertThatThrownBy(() -> converter.toPng(PICTURE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not available on this server");
    }
}
