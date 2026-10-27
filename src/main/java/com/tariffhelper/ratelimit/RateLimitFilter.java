package com.tariffhelper.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Limits only the POST endpoints that can call the LLM ({@link #LIMITED_PATHS}). They share one budget.
 * Answers 429 when over a limit.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    public static final List<String> LIMITED_PATHS = List.of("/api/estimates", "/api/payslips/parse");

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && LIMITED_PATHS.contains(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimiter.Result result = limiter.tryAcquire(request.getRemoteAddr());
        if (result.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        String detail = result.decision() == RateLimiter.Decision.DAILY_LIMIT
                ? "The service has reached its daily limit. Please try again tomorrow."
                : "Too many requests. Please wait a minute and try again.";
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(result.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"" + detail + "\"}");
    }
}
