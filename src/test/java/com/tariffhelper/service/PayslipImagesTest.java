package com.tariffhelper.service;

import com.tariffhelper.TestImages;
import com.tariffhelper.TestPdf;
import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayslipImagesTest {

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void readsTheOrientationFromAnExifTag() {
        assertThat(PayslipImages.exifOrientation(TestImages.jpegWithOrientation(40, 20, 6))).isEqualTo(6);
        assertThat(PayslipImages.exifOrientation(TestImages.jpegWithOrientation(40, 20, 3))).isEqualTo(3);
    }

    @Test
    void aJpegWithoutExifOrWithJunkCountsAsUpright() {
        assertThat(PayslipImages.exifOrientation(TestImages.jpeg(40, 20))).isEqualTo(1);
        assertThat(PayslipImages.exifOrientation(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 5}))
                .isEqualTo(1);
    }

    @Test
    void aSidewaysPhotoIsTurnedUpright() throws IOException {
        // 400 wide, 200 high, marked "turn 90 degrees": the page is really 200 wide and 400 high.
        byte[] prepared = PayslipImages.prepare(TestImages.jpegWithOrientation(400, 200, 6), PayslipFileType.JPEG);

        BufferedImage image = decode(prepared);
        assertThat(image.getHeight()).isGreaterThan(image.getWidth());
    }

    @Test
    void aPhotoWithoutATurnKeepsItsShape() throws IOException {
        BufferedImage image = decode(PayslipImages.prepare(TestImages.jpeg(400, 200), PayslipFileType.JPEG));

        assertThat(image.getWidth()).isGreaterThan(image.getHeight());
    }

    @Test
    void largePicturesAreScaledDownAndSmallOnesUp() throws IOException {
        BufferedImage large = decode(PayslipImages.prepare(TestImages.png(3000, 1500), PayslipFileType.PNG));
        BufferedImage small = decode(PayslipImages.prepare(TestImages.png(300, 200), PayslipFileType.PNG));

        assertThat(Math.max(large.getWidth(), large.getHeight())).isEqualTo(PayslipImages.MAX_SIDE);
        assertThat(Math.max(small.getWidth(), small.getHeight())).isEqualTo(600);
    }

    @Test
    void thePreparedPictureIsGrey() throws IOException {
        BufferedImage image = decode(PayslipImages.prepare(TestImages.png(1200, 800), PayslipFileType.PNG));

        assertThat(image.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
    }

    @Test
    void rejectsSomethingThatIsNotAPictureWithoutEchoingIt() {
        byte[] notAPicture = "secret-looking text 01019012345".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PayslipImages.prepare(notAPicture, PayslipFileType.PNG))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("The picture could not be read.");
    }

    @Test
    void rejectsAPictureWithTooManyPixelsBeforeDecodingIt() {
        byte[] huge = TestImages.png(6000, 5000);

        assertThatThrownBy(() -> PayslipImages.prepare(huge, PayslipFileType.PNG))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void rendersTheFirstPagesOfAScannedPdf() throws IOException {
        List<byte[]> pages = PayslipImages.renderPdfPages(TestPdf.pages(3, List.of()));

        assertThat(pages).hasSize(PayslipImages.MAX_PDF_PAGES_TO_READ);
        assertThat(decode(pages.get(0)).getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
    }
}
