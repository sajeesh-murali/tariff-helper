package com.tariffhelper.service;

/** The kind of an uploaded payslip, decided from the first bytes of the file, never from its name or declared type. */
public enum PayslipFileType {
    PDF,
    PNG,
    JPEG,
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
        return UNKNOWN;
    }

    public boolean isImage() {
        return this == PNG || this == JPEG;
    }
}
