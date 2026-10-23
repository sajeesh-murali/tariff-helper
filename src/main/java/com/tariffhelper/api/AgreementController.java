package com.tariffhelper.api;

import com.tariffhelper.agreement.AgreementRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Supplies the options for the form dropdowns. Agreements with status "placeholder" cannot be estimated yet. */
@RestController
@RequestMapping("/api/agreements")
public class AgreementController {

    private final AgreementRepository repository;

    public AgreementController(AgreementRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<AgreementSummary> list() {
        return repository.findAll().stream().map(AgreementSummary::from).toList();
    }
}
