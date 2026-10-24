package com.tariffhelper.api;
import com.tariffhelper.api.dto.EstimateResponse;
import com.tariffhelper.api.dto.EstimateRequest;

import com.tariffhelper.service.EstimateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/estimates")
public class EstimateController {

    private final EstimateService estimateService;

    public EstimateController(EstimateService estimateService) {
        this.estimateService = estimateService;
    }

    @PostMapping
    public EstimateResponse estimate(@Valid @RequestBody EstimateRequest request) {
        return estimateService.estimate(request);
    }
}
