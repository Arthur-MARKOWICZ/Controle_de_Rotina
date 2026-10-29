package com.controlerotina.config.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final JsonMapper PROBLEM_JSON = JsonMapper.builder().build();

    private final RateLimitProperties properties;
    private final IpRateLimiter limiter;

    public AuthRateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.limiter = new IpRateLimiter(
                Math.max(properties.requestsPerMinute(), 1),
                Duration.ofMinutes(1),
                clock
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return !"/auth/login".equals(path) && !"/usuarios".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!limiter.tryAcquire(clientIp(request))) {
            writeTooManyRequests(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static void writeTooManyRequests(HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Muitas tentativas");
        problem.setDetail("Limite de tentativas excedido. Aguarde um minuto e tente novamente.");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        PROBLEM_JSON.writeValue(response.getOutputStream(), problem);
    }
}
