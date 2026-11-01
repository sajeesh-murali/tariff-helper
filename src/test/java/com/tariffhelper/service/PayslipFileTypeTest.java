package com.tariffhelper.service;

import com.tariffhelper.TestImages;
import com.tariffhelper.TestPdf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PayslipFileTypeTest {

    @Test
    void recognisesTheTypeFromTheFirstBytesNotTheName() {
        assertThat(PayslipFileType.detect(TestPdf.samplePayslip())).isEqualTo(PayslipFileType.PDF);
        assertThat(PayslipFileType.detect(TestImages.png(20, 20))).isEqualTo(PayslipFileType.PNG);
        assertThat(PayslipFileType.detect(TestImages.jpeg(20, 20))).isEqualTo(PayslipFileType.JPEG);
    }

    @Test
    void anythingElseIsUnknown() {
        assertThat(PayslipFileType.detect("just some text, not a file".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(PayslipFileType.UNKNOWN);
        assertThat(PayslipFileType.detect(new byte[0])).isEqualTo(PayslipFileType.UNKNOWN);
        assertThat(PayslipFileType.detect(null)).isEqualTo(PayslipFileType.UNKNOWN);
    }

    @Test
    void onlyPicturesAreImages() {
        assertThat(PayslipFileType.PNG.isImage()).isTrue();
        assertThat(PayslipFileType.JPEG.isImage()).isTrue();
        assertThat(PayslipFileType.PDF.isImage()).isFalse();
        assertThat(PayslipFileType.UNKNOWN.isImage()).isFalse();
    }
}
