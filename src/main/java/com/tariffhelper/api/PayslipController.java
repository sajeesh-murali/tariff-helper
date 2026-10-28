package com.tariffhelper.api;

import com.tariffhelper.api.dto.response.PayslipResponse;
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

/** Reads the form values from an uploaded payslip PDF so the person can check them. Nothing is stored. */
@RestController
@RequestMapping("/api/payslips")
public class PayslipController {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private final PayslipService service;

    public PayslipController(PayslipService service) {
        this.service = service;
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PayslipResponse parse(@RequestParam("file") MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large (at most 2 MB).");
        }
        try {
            return PayslipResponse.from(service.parse(file.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
