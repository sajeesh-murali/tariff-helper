package com.tariffhelper.service;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Reads the text layer of a payslip PDF. The file is handled in memory only and is never written to disk.
 * Scanned payslips (no text layer) are not supported yet.
 *
 * Privacy: error messages never echo anything from the file.
 */
@Component
public class PayslipTextReader {

    static final int MAX_PAGES = 5;
    static final int MAX_CHARS = 50_000;

    public String read(byte[] pdf) {
        if (!looksLikePdf(pdf)) {
            throw new InvalidRequestException("The file is not a PDF.");
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new InvalidRequestException("The PDF has too many pages for a payslip (at most "
                        + MAX_PAGES + ").");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text.isBlank()) {
                throw new InvalidRequestException(
                        "The PDF has no readable text. Scanned payslips are not supported yet.");
            }
            return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS);
        } catch (IOException e) {
            throw new InvalidRequestException("The PDF could not be read.");
        }
    }

    private static boolean looksLikePdf(byte[] bytes) {
        return bytes != null && bytes.length > 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }
}
