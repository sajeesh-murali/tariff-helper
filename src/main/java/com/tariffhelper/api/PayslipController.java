package com.tariffhelper.api;

import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import com.tariffhelper.api.dto.response.PayslipResponse;
import com.tariffhelper.service.PayslipFileType;
import com.tariffhelper.service.PayslipService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Reads the form values from an uploaded payslip (PDF, JPG, PNG, WebP, TIFF, HEIC or HEIF) so the person can check them. Nothing is stored.
 * A password for a protected PDF is used only to open the file; it is never logged, stored or echoed.
 */
@RestController
@RequestMapping("/api/payslips")
public class PayslipController {

    static final int MAX_PASSWORD_CHARS = 128;
    static final long MAX_PDF_BYTES = 2L * 1024 * 1024;
    /** Phone photos are larger than PDFs; they are scaled down on the server before they are read. */
    static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    private final PayslipService service;

    public PayslipController(PayslipService service) {
        this.service = service;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PayslipResponse parse(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "password", required = false) String password) {
        if (password != null && password.length() > MAX_PASSWORD_CHARS) {
            throw new InvalidRequestException("The password is too long.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "The file is too large (at most 2 MB for a PDF, 8 MB for a picture).");
        }
        try {
            byte[] bytes = file.getBytes();
            if (PayslipFileType.detect(bytes) == PayslipFileType.PDF && bytes.length > MAX_PDF_BYTES) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The PDF is too large (at most 2 MB).");
            }
            return PayslipResponse.from(service.parse(bytes, password));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
