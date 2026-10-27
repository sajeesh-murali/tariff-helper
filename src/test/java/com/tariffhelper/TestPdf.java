package com.tariffhelper;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Builds small PDFs with a text layer for tests. Only invented data, never a real payslip. */
public final class TestPdf {

    private TestPdf() {
    }

    /** One page holding the given lines. */
    public static byte[] withLines(List<String> lines) {
        return pages(1, lines);
    }

    /** The given number of pages; the lines go on the first page only. */
    public static byte[] pages(int pageCount, List<String> lines) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (i == 0 && !lines.isEmpty()) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                        content.setLeading(14f);
                        content.newLineAtOffset(40, 750);
                        for (String line : lines) {
                            content.showText(line);
                            content.newLine();
                        }
                        content.endText();
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A payslip-like page with invented personal data, so tests can prove it is not passed on. */
    public static byte[] samplePayslip() {
        return withLines(List.of(
                "Lønnsslipp august 2026",
                "Navn: Kari Testesen",
                "Fødselsnummer: 01019012345",
                "Kontonummer: 4202.15.12345",
                "Arbeidsgiver: Oslo kommune",
                "Stilling: Sykepleier",
                "Stillingsprosent: 80 %",
                "Årslønn i 100 % stilling før skatt 612 300,00",
                "Registrert ansiennitet: 01.08.2015",
                "Stillingskode Stilling Lonnstrinn Ansiennitet fra",
                "7936 Sykepleier 45 01.08.2015",
                "Skattetrekk 12 345,00"));
    }
}
