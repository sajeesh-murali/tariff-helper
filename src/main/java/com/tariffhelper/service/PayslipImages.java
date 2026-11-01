package com.tariffhelper.service;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gets a picture of a payslip ready for text recognition: checks the size before decoding it, turns a phone photo
 * the right way up, makes it grey and a sensible size. Everything stays in memory.
 *
 * Privacy: error messages never contain anything from the file.
 */
public final class PayslipImages {

    /** Refuses pictures that would need too much memory to decode. */
    static final long MAX_PIXELS = 25_000_000L;
    /** Larger pictures are scaled down to this on the longest side; it keeps memory and time in check. */
    static final int MAX_SIDE = 2000;
    /** Smaller pictures are enlarged, because small text reads badly. */
    static final int MIN_SIDE = 1000;
    static final int PDF_DPI = 200;
    static final int MAX_PDF_PAGES_TO_READ = 2;

    private PayslipImages() {
    }

    /** A PNG ready for the OCR engine. */
    public static byte[] prepare(byte[] file, PayslipFileType type) {
        BufferedImage image = decode(file);
        if (type == PayslipFileType.JPEG) {
            image = rotateUpright(image, exifOrientation(file));
        }
        return toPng(fit(image));
    }

    /** The first pages of a scanned PDF as PNGs ready for the OCR engine. */
    public static List<byte[]> renderPdfPages(byte[] pdf) {
        return renderPdfPages(pdf, null);
    }

    public static List<byte[]> renderPdfPages(byte[] pdf, String password) {
        try (PDDocument document = Loader.loadPDF(pdf, password == null ? "" : password)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = Math.min(document.getNumberOfPages(), MAX_PDF_PAGES_TO_READ);
            List<byte[]> result = new ArrayList<>();
            for (int i = 0; i < pages; i++) {
                result.add(toPng(fit(renderer.renderImageWithDPI(i, PDF_DPI, ImageType.GRAY))));
            }
            return result;
        } catch (IOException | RuntimeException e) {
            throw new InvalidRequestException("The PDF could not be read.");
        }
    }

    private static BufferedImage decode(byte[] file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new InvalidRequestException("The picture could not be read.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new InvalidRequestException("The picture is too large. Use a smaller picture.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidRequestException invalid) {
                throw invalid;
            }
            throw new InvalidRequestException("The picture could not be read.");
        }
    }

    /** Scales to a sensible size and converts to grey on a white background (so transparency does not go black). */
    static BufferedImage fit(BufferedImage source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double factor = 1.0;
        if (longest > MAX_SIDE) {
            factor = (double) MAX_SIDE / longest;
        } else if (longest < MIN_SIDE) {
            factor = Math.min(2.0, (double) MIN_SIDE / longest);
        }
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage grey = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = grey.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return grey;
    }

    private static byte[] toPng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IOException("no PNG writer");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new InvalidRequestException("The picture could not be read.");
        }
    }

    /** Phone cameras often store the picture sideways and only note the turn in the EXIF data. */
    static BufferedImage rotateUpright(BufferedImage image, int orientation) {
        int degrees = switch (orientation) {
            case 3 -> 180;
            case 6 -> 90;
            case 8 -> 270;
            default -> 0;
        };
        if (degrees == 0) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean sideways = degrees != 180;
        BufferedImage turned = new BufferedImage(sideways ? h : w, sideways ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = turned.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, turned.getWidth(), turned.getHeight());
            AffineTransform t = new AffineTransform();
            t.translate(turned.getWidth() / 2.0, turned.getHeight() / 2.0);
            t.rotate(Math.toRadians(degrees));
            t.translate(-w / 2.0, -h / 2.0);
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return turned;
    }

    /** The EXIF orientation of a JPEG (1 = as is), or 1 when it is missing or cannot be read. */
    static int exifOrientation(byte[] jpeg) {
        int i = 2;
        while (i + 4 <= jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int marker = jpeg[i + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                break;
            }
            int length = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
            if (marker == 0xE1 && length >= 8 && i + 10 <= jpeg.length
                    && jpeg[i + 4] == 'E' && jpeg[i + 5] == 'x' && jpeg[i + 6] == 'i' && jpeg[i + 7] == 'f'
                    && jpeg[i + 8] == 0 && jpeg[i + 9] == 0) {
                return orientationFromTiff(jpeg, i + 10, Math.min(jpeg.length, i + 2 + length));
            }
            i += 2 + length;
        }
        return 1;
    }

    private static int orientationFromTiff(byte[] b, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        if (!little && !(b[tiff] == 'M' && b[tiff + 1] == 'M')) {
            return 1;
        }
        long ifd = u32(b, tiff + 4, little);
        long entriesAt = tiff + ifd;
        if (ifd < 8 || entriesAt + 2 > end) {
            return 1;
        }
        int count = u16(b, (int) entriesAt, little);
        for (int n = 0; n < count; n++) {
            long entry = entriesAt + 2 + 12L * n;
            if (entry + 12 > end) {
                return 1;
            }
            if (u16(b, (int) entry, little) == 0x0112) {
                int value = u16(b, (int) entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u16(byte[] b, int at, boolean little) {
        int first = b[at] & 0xFF;
        int second = b[at + 1] & 0xFF;
        return little ? (second << 8) | first : (first << 8) | second;
    }

    private static long u32(byte[] b, int at, boolean little) {
        long first = u16(b, at, little);
        long second = u16(b, at + 2, little);
        return little ? (second << 16) | first : (first << 16) | second;
    }
}
