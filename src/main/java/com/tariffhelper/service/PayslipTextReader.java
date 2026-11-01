package com.tariffhelper.service;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Reads the text layer of a payslip PDF. The file is handled in memory only and is never written to disk.
 * A PDF without a text layer (a scan) has no text here; {@link PayslipFileReader} sends those to OCR.
 *
 * Privacy: error messages never echo anything from the file.
 */
@Component
public class PayslipTextReader {

    static final int MAX_PAGES = 5;
    static final String PASSWORD_REQUIRED = "The PDF is password protected. Send the password in the 'password' field.";
    static final String PASSWORD_WRONG = "The password is wrong.";
    static final int MAX_CHARS = 50_000;

    /** The text layer, or empty when the PDF has none (a scanned payslip). */
    public java.util.Optional<String> textLayer(byte[] pdf) {
        return textLayer(pdf, null);
    }

    /** @param password the PDF's password, or null/empty when the person gave none; never logged or echoed */
    public java.util.Optional<String> textLayer(byte[] pdf, String password) {
        if (!looksLikePdf(pdf)) {
            throw new InvalidRequestException("The file is not a PDF.");
        }
        try (PDDocument document = Loader.loadPDF(pdf, password == null ? "" : password)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new InvalidRequestException("The PDF has too many pages for a payslip (at most "
                        + MAX_PAGES + ").");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text.isBlank()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS));
        } catch (InvalidPasswordException e) {
            throw new InvalidRequestException(password == null || password.isEmpty() ? PASSWORD_REQUIRED : PASSWORD_WRONG);
        } catch (IOException e) {
            throw new InvalidRequestException("The PDF could not be read.");
        }
    }

    public String read(byte[] pdf) {
        return textLayer(pdf).orElseThrow(() -> new InvalidRequestException(
                "The PDF has no readable text. Scanned payslips need picture reading."));
    }

    private static boolean looksLikePdf(byte[] bytes) {
        return bytes != null && bytes.length > 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }
}
