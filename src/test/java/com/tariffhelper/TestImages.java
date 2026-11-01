package com.tariffhelper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Builds small pictures for tests. Plain shapes only, never a real payslip. */
public final class TestImages {

    private TestImages() {
    }

    public static BufferedImage blank(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, Math.max(1, width / 4), Math.max(1, height / 4));
        g.dispose();
        return image;
    }

    public static byte[] png(int width, int height) {
        return encode(blank(width, height, BufferedImage.TYPE_INT_RGB), "png");
    }

    public static byte[] jpeg(int width, int height) {
        return encode(blank(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    /** A JPEG that carries an EXIF orientation tag, like a phone photo taken with the phone turned. */
    public static byte[] jpegWithOrientation(int width, int height, int orientation) {
        byte[] plain = jpeg(width, height);
        byte[] app1 = {
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x22,                      // APP1, length 34
                'E', 'x', 'i', 'f', 0, 0,                                  // Exif header
                'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00,              // TIFF header, little endian, IFD at 8
                0x01, 0x00,                                                // one entry
                0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00,            // tag 0x0112, SHORT, count 1
                (byte) orientation, 0x00, 0x00, 0x00,                      // the value
                0x00, 0x00, 0x00, 0x00                                     // no next IFD
        };
        byte[] result = new byte[plain.length + app1.length];
        System.arraycopy(plain, 0, result, 0, 2);
        System.arraycopy(app1, 0, result, 2, app1.length);
        System.arraycopy(plain, 2, result, 2 + app1.length, plain.length - 2);
        return result;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
