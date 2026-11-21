package com.tariffhelper.ocr;

/** Turns a picture of a payslip into text. Runs on the server; the picture is never sent to a third party. */
public interface OcrEngine {

    /**
     * @param png a prepared picture (grey, not too large)
     * @return the recognised text, never blank
     * @throws com.tariffhelper.api.ApiExceptions.InvalidRequestException when the text cannot be read; the message
     *                                                                     never contains anything from the picture
     */
    String recognize(byte[] png);

    /**
     * A second reading of the same picture in Tesseract's "one column of text" page mode. It can keep narrow table
     * columns that the default mode drops (the Ltr step on an Oslo payslip). The default is the ordinary reading.
     */
    default String recognizeAsColumns(byte[] png) {
        return recognize(png);
    }
}
