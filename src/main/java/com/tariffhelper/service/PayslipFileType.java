package com.tariffhelper.service;

import java.nio.charset.StandardCharsets;

/** The kind of an uploaded payslip, decided from the first bytes of the file, never from its name or declared type. */
public enum PayslipFileType {
    PDF,
    PNG,
    JPEG,
    WEBP,
    TIFF,
    /** HEIC and HEIF: the same container; iPhones save photos this way. */
    HEIC,
    UNKNOWN;

    public static PayslipFileType detect(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return UNKNOWN;
        }
        if (bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            return PDF;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return PNG;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return WEBP;
        }
        boolean tiffLittle = bytes[0] == 'I' && bytes[1] == 'I' && bytes[2] == 42 && bytes[3] == 0;
        boolean tiffBig = bytes[0] == 'M' && bytes[1] == 'M' && bytes[2] == 0 && bytes[3] == 42;
        if (tiffLittle || tiffBig) {
            return TIFF;
        }
        if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p'
                && isHeifBrand(new String(bytes, 8, 4, StandardCharsets.ISO_8859_1))) {
            return HEIC;
        }
        return UNKNOWN;
    }

    private static boolean isHeifBrand(String brand) {
        return switch (brand) {
            case "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1" -> true;
            default -> false;
        };
    }

    public boolean isImage() {
        return this != PDF && this != UNKNOWN;
    }
}
