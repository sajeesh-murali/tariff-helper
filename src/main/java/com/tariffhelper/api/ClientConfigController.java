package com.tariffhelper.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settings the web page needs and that differ between environments. Nothing here is secret or personal.
 *
 * @param bookingUrl the page where a person books a meeting, or null when none is set up (the page then hides the
 *                   booking button). Only an https address is passed on.
 */
@RestController
@RequestMapping("/api/config")
public class ClientConfigController {

    public record ClientConfig(String bookingUrl) {
    }

    private final String bookingUrl;

    public ClientConfigController(@Value("${app.booking.url:}") String bookingUrl) {
        String trimmed = bookingUrl == null ? "" : bookingUrl.strip();
        this.bookingUrl = trimmed.startsWith("https://") ? trimmed : null;
    }

    @GetMapping
    public ClientConfig get() {
        return new ClientConfig(bookingUrl);
    }
}
