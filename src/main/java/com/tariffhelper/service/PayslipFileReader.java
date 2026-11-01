package com.tariffhelper.service;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import com.tariffhelper.ocr.OcrEngine;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns an uploaded payslip into text, choosing the way from what the file really is (its first bytes, not its
 * name): a PDF with a text layer is read directly; a picture, or a scanned PDF without a text layer, goes through
 * OCR on this server. Nothing is stored and nothing is sent to a third party.
 */
@Component
public class PayslipFileReader {

    static final int MAX_CHARS = 50_000;

    private final PayslipTextReader pdfReader;
    private final OcrEngine ocr;

    public PayslipFileReader(PayslipTextReader pdfReader, OcrEngine ocr) {
        this.pdfReader = pdfReader;
        this.ocr = ocr;
    }

    public String read(byte[] file) {
        return read(file, null);
    }

    /** @param password for a password-protected PDF; null otherwise. Ignored for pictures. Never logged. */
    public String read(byte[] file, String password) {
        PayslipFileType type = PayslipFileType.detect(file);
        return switch (type) {
            case PDF -> pdfReader.textLayer(file, password)
                    .orElseGet(() -> recognise(PayslipImages.renderPdfPages(file, password)));
            case PNG, JPEG -> recognise(List.of(PayslipImages.prepare(file, type)));
            case UNKNOWN -> throw new InvalidRequestException("The file must be a PDF, JPG or PNG.");
        };
    }

    private String recognise(List<byte[]> pictures) {
        StringBuilder text = new StringBuilder();
        for (byte[] picture : pictures) {
            text.append(ocr.recognize(picture)).append('\n');
        }
        String all = text.toString();
        return all.length() <= MAX_CHARS ? all : all.substring(0, MAX_CHARS);
    }
}
