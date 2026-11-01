package com.tariffhelper.service;

import com.tariffhelper.TestImages;
import com.tariffhelper.TestPdf;
import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import com.tariffhelper.ocr.OcrEngine;
import com.tariffhelper.ocr.OcrProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The OCR engine is replaced by a stand-in, so no Tesseract is needed. */
class PayslipFileReaderTest {

    private final List<byte[]> seenByOcr = new ArrayList<>();

    private PayslipFileReader readerWith(OcrEngine ocr) {
        return new PayslipFileReader(new PayslipTextReader(), ocr, new OcrProperties(true, "tesseract", "nor+eng", 60, 3000));
    }

    private OcrEngine recording(String text) {
        return png -> {
            seenByOcr.add(png);
            return text;
        };
    }

    @Test
    void aPdfWithATextLayerIsReadDirectlyWithoutOcr() {
        String text = readerWith(recording("never used")).read(TestPdf.samplePayslip());

        assertThat(text).contains("7936 Sykepleier 45 01.08.2015");
        assertThat(seenByOcr).isEmpty();
    }

    @Test
    void lettersMistakenForDigitsInNumbersAreFixed() {
        String text = readerWith(recording("Registrert ansiennitet: 01.08.2O15\nÅrslønn 612 3OO,OO\nStilling: Oslo"))
                .read(TestImages.png(1200, 800));

        assertThat(text).contains("01.08.2015").contains("612 300,00").contains("Stilling: Oslo");
    }

    @Test
    void aPictureGoesThroughOcr() {
        String text = readerWith(recording("Arbeidsgiver: Oslo kommune")).read(TestImages.png(1200, 800));

        assertThat(text).contains("Arbeidsgiver: Oslo kommune");
        assertThat(seenByOcr).hasSize(1);
        assertThat(PayslipFileType.detect(seenByOcr.get(0))).isEqualTo(PayslipFileType.PNG);
    }

    @Test
    void aJpegPhotoGoesThroughOcrToo() {
        String text = readerWith(recording("Stilling: Sykepleier")).read(TestImages.jpegWithOrientation(1200, 800, 6));

        assertThat(text).contains("Stilling: Sykepleier");
        assertThat(seenByOcr).hasSize(1);
    }

    @Test
    void aScannedPdfFallsBackToOcrOnItsPages() {
        String text = readerWith(recording("Arbeidsgiver: Bergen kommune")).read(TestPdf.pages(3, List.of()));

        assertThat(text).contains("Arbeidsgiver: Bergen kommune");
        assertThat(seenByOcr).hasSize(PayslipImages.MAX_PDF_PAGES_TO_READ);
    }

    @Test
    void aProtectedScannedPdfIsOpenedWithThePasswordThenReadWithOcr() {
        byte[] protectedScan = TestPdf.withPassword(TestPdf.pages(1, List.of()), "hemmelig");

        String text = readerWith(recording("Arbeidsgiver: Oslo kommune")).read(protectedScan, "hemmelig");

        assertThat(text).contains("Arbeidsgiver: Oslo kommune");
        assertThat(seenByOcr).hasSize(1);
    }

    @Test
    void aProtectedPdfWithoutThePasswordNeverReachesOcr() {
        byte[] protectedPdf = TestPdf.withPassword(TestPdf.samplePayslip(), "hemmelig");

        assertThatThrownBy(() -> readerWith(recording("x")).read(protectedPdf, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(PayslipTextReader.PASSWORD_REQUIRED);
        assertThat(seenByOcr).isEmpty();
    }

    @Test
    void somethingThatIsNotAPayslipFileIsRejectedWithoutEchoingIt() {
        byte[] other = "secret-looking text 01019012345".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> readerWith(recording("x")).read(other))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The file must be a PDF, JPG or PNG.");
        assertThat(seenByOcr).isEmpty();
    }

    @Test
    void anOcrFailureIsPassedOn() {
        OcrEngine failing = png -> {
            throw new InvalidRequestException("No text was found in the picture. Try a clearer picture.");
        };

        assertThatThrownBy(() -> readerWith(failing).read(TestImages.png(1200, 800)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("No text was found");
    }
}
