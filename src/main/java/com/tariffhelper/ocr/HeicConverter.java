package com.tariffhelper.ocr;

/** Turns an HEIC/HEIF picture (the iPhone format) into a PNG, because Java cannot decode it. Runs on the server. */
public interface HeicConverter {

    /**
     * @param heic the uploaded file
     * @return a PNG, turned upright and not larger than the OCR size limit
     * @throws com.tariffhelper.api.ApiExceptions.InvalidRequestException when it cannot be converted; the message
     *                                                                     never contains anything from the picture
     */
    byte[] toPng(byte[] heic);
}
