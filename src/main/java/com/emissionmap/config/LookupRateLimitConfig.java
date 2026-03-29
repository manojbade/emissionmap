package com.emissionmap.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Profile("prod")
public class LookupRateLimitConfig {

    private static final long LOOKUPS_PER_MINUTE = 20;

    @Bean
    public FilterRegistrationBean<Filter> lookupRateLimitFilter() {
        Map<String, Bucket> buckets = new ConcurrentHashMap<>();
        Bandwidth limit = Bandwidth.classic(
                LOOKUPS_PER_MINUTE,
                Refill.greedy(LOOKUPS_PER_MINUTE, Duration.ofMinutes(1))
        );

        Filter filter = new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                if (!(request instanceof HttpServletRequest httpRequest)
                        || !(response instanceof HttpServletResponse httpResponse)) {
                    chain.doFilter(request, response);
                    return;
                }

                if (!"POST".equalsIgnoreCase(httpRequest.getMethod())) {
                    chain.doFilter(request, response);
                    return;
                }

                String clientKey = clientKey(httpRequest);
                Bucket bucket = buckets.computeIfAbsent(clientKey, ignored -> Bucket.builder().addLimit(limit).build());

                if (bucket.tryConsume(1)) {
                    chain.doFilter(request, response);
                    return;
                }

                httpResponse.setStatus(429);
                httpResponse.setContentType("text/plain;charset=UTF-8");
                httpResponse.setHeader("Retry-After", "60");
                httpResponse.getWriter().write("Too many address lookups. Please wait a minute and try again.");
            }
        };

        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/lookup");
        registration.setName("lookupRateLimitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    private static String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr == null || remoteAddr.isBlank()) ? "unknown" : remoteAddr;
    }
}
