package com.tariffhelper.api;
import com.tariffhelper.api.dto.response.AgreementSummary;
import com.tariffhelper.api.dto.response.SuggestResponse;
import com.tariffhelper.api.dto.request.SuggestRequest;

import com.tariffhelper.agreement.AgreementRepository;
import com.tariffhelper.service.AgreementSuggestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Supplies the options for the form dropdowns. Agreements with status "placeholder" cannot be estimated yet.
 * Also suggests which agreement applies, from the employer name.
 */
@RestController
@RequestMapping("/api/agreements")
public class AgreementController {

    private final AgreementRepository repository;
    private final AgreementSuggestionService suggestionService;

    public AgreementController(AgreementRepository repository, AgreementSuggestionService suggestionService) {
        this.repository = repository;
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public List<AgreementSummary> list() {
        return repository.findAll().stream().map(AgreementSummary::from).toList();
    }

    @PostMapping("/suggest")
    public SuggestResponse suggest(@Valid @RequestBody SuggestRequest request) {
        return suggestionService.suggest(request.employerName(), request.jobTitle());
    }
}
