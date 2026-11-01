package com.tariffhelper.ocr;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only the failure paths: they need no Tesseract installed. Real recognition is checked where it is installed. */
class TesseractOcrEngineTest {

    private static final byte[] PICTURE = {1, 2, 3};

    @Test
    void saysSoWhenPictureReadingIsTurnedOff() {
        TesseractOcrEngine engine = new TesseractOcrEngine(new OcrProperties(false, "tesseract", "nor+eng", 5));

        assertThatThrownBy(() -> engine.recognize(PICTURE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not available on this server");
    }

    @Test
    void saysSoWhenTheProgramIsNotInstalled() {
        TesseractOcrEngine engine =
                new TesseractOcrEngine(new OcrProperties(true, "no-such-ocr-program-xyz", "nor+eng", 5));

        assertThatThrownBy(() -> engine.recognize(PICTURE))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not available on this server");
    }
}
