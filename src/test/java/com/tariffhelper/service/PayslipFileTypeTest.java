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
        assertThat(PayslipFileType.detect(TestImages.tiff(20, 20))).isEqualTo(PayslipFileType.TIFF);
        assertThat(PayslipFileType.detect(TestImages.webp())).isEqualTo(PayslipFileType.WEBP);
    }

    @Test
    void recognisesHeicAndHeifByTheirBrand() {
        for (String brand : new String[] {"heic", "heix", "hevc", "mif1", "msf1"}) {
            assertThat(PayslipFileType.detect(TestImages.heicHeader(brand))).as(brand).isEqualTo(PayslipFileType.HEIC);
        }
        assertThat(PayslipFileType.detect(TestImages.heicHeader("mp42"))).isEqualTo(PayslipFileType.UNKNOWN);
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
        assertThat(PayslipFileType.WEBP.isImage()).isTrue();
        assertThat(PayslipFileType.TIFF.isImage()).isTrue();
        assertThat(PayslipFileType.HEIC.isImage()).isTrue();
        assertThat(PayslipFileType.PDF.isImage()).isFalse();
        assertThat(PayslipFileType.UNKNOWN.isImage()).isFalse();
    }
}
