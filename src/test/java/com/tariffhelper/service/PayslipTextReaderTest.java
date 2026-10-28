package com.tariffhelper.service;

import com.tariffhelper.TestPdf;
import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayslipTextReaderTest {

    private final PayslipTextReader reader = new PayslipTextReader();

    @Test
    void readsTheTextLayerKeepingEachRowTogether() {
        String text = reader.read(TestPdf.samplePayslip());

        assertThat(text).contains("7936 Sykepleier 45 01.08.2015");
    }

    @Test
    void rejectsAFileThatIsNotAPdfWithoutEchoingIt() {
        byte[] notPdf = "secret-looking text 01019012345".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(notPdf))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The file is not a PDF.");
    }

    @Test
    void rejectsACorruptPdf() {
        byte[] broken = "%PDF-1.7 this is not really a pdf".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> reader.read(broken))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The PDF could not be read.");
    }

    @Test
    void rejectsAPdfWithoutATextLayerAsScanned() {
        assertThatThrownBy(() -> reader.read(TestPdf.pages(1, List.of())))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Scanned payslips are not supported yet");
    }

    @Test
    void rejectsTooManyPages() {
        assertThatThrownBy(() -> reader.read(TestPdf.pages(PayslipTextReader.MAX_PAGES + 1, List.of("Stilling"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("too many pages");
    }
}
