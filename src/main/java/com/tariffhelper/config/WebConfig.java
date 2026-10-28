package com.tariffhelper.config;

import com.tariffhelper.ratelimit.RateLimitFilter;
import com.tariffhelper.ratelimit.RateLimitProperties;
import com.tariffhelper.ratelimit.RateLimiter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;
import java.util.List;

/**
 * Rate limiting, CORS and the static site. CORS is closed unless {@code app.cors.allowed-origins} lists origins.
 * Spring Boot serves index.html only at the root, so each folder page of the static site is forwarded here.
 */
@Configuration
@EnableConfigurationProperties({RateLimitProperties.class, WebConfig.CorsProperties.class})
public class WebConfig implements WebMvcConfigurer {

    @ConfigurationProperties(prefix = "app.cors")
    public record CorsProperties(List<String> allowedOrigins) {
        public CorsProperties {
            allowedOrigins = allowedOrigins == null ? List.of()
                    : allowedOrigins.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    /** Folders under static/ that hold an index.html (the home page is served by Spring Boot itself). */
    public static final List<String> PAGE_FOLDERS = List.of("guider", "lonnsansiennitet", "feil-lonnsansiennitet",
            "ansiennitet/barnehage", "ansiennitet/barnehageassistent", "ansiennitet/helsefagarbeider",
            "ansiennitet/kommune", "ansiennitet/sykehus", "ansiennitet/sykepleier");

    private final CorsProperties cors;

    public WebConfig(CorsProperties cors) {
        this.cors = cors;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        for (String folder : PAGE_FOLDERS) {
            registry.addViewController("/" + folder).setViewName("forward:/" + folder + "/index.html");
            registry.addViewController("/" + folder + "/").setViewName("forward:/" + folder + "/index.html");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (cors.allowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitProperties props) {
        RateLimiter limiter = new RateLimiter(Clock.systemUTC(), props.perClientPerMinute(), props.globalPerDay());
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(new RateLimitFilter(limiter));
        registration.setEnabled(props.enabled());
        registration.addUrlPatterns(RateLimitFilter.LIMITED_PATHS.toArray(String[]::new));
        return registration;
    }
}
