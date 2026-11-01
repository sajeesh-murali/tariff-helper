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
}
