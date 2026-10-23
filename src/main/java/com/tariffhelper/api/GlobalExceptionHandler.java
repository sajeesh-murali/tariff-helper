package com.tariffhelper.api;

import com.tariffhelper.api.ApiExceptions.AgreementNotAvailableException;
import com.tariffhelper.api.ApiExceptions.AgreementNotDeterminedException;
import com.tariffhelper.api.ApiExceptions.AgreementNotFoundException;
import com.tariffhelper.api.ApiExceptions.InvalidRequestException;
import com.tariffhelper.llm.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Privacy: error responses and logs never echo submitted values
 * (salary, dates, etc.), only field names and generic messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request body is missing or not valid JSON (check field types and date format yyyy-MM)");
    }

    @ExceptionHandler(AgreementNotFoundException.class)
    public ProblemDetail handleAgreementNotFound(AgreementNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AgreementNotAvailableException.class)
    public ProblemDetail handleAgreementNotAvailable(AgreementNotAvailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(AgreementNotDeterminedException.class)
    public ProblemDetail handleAgreementNotDetermined(AgreementNotDeterminedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setProperty("availableAgreements", ex.availableAgreements());
        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalid(InvalidRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(LlmException.class)
    public ProblemDetail handleLlmFailure(LlmException ex) {
        // The client has already logged the cause (status code only, never content).
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The estimate service is temporarily unavailable. Please try again later.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Spring's own web errors (unknown path 404, wrong method 405, wrong content type 415, ...)
        // already know their status; pass them through instead of turning them into a 500.
        if (ex instanceof ErrorResponse errorResponse) {
            return errorResponse.getBody();
        }
        // Log only the exception type, not the message, to avoid leaking request data.
        log.error("Unexpected error: {}", ex.getClass().getName());
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again later.");
    }
}
