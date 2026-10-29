package com.controlerotina.config.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuthRateLimitFilterTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    private AuthRateLimitFilter filter;
    private AtomicInteger chainCalls;
    private FilterChain countingChain;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter(new RateLimitProperties(true, 5), CLOCK);
        chainCalls = new AtomicInteger();
        countingChain = (request, response) -> chainCalls.incrementAndGet();
    }

    @Test
    void deveRetornar429QuandoLoginExcedeLimite() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(loginRequest("10.0.0.1"), ok, countingChain);
            assertThat(ok.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        assertThat(chainCalls.get()).isEqualTo(5);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest("10.0.0.1"), blocked, countingChain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        JsonNode body = JSON.readTree(blocked.getContentAsByteArray());
        assertThat(body.get("status").asInt()).isEqualTo(429);
        assertThat(body.get("title").asString()).containsIgnoringCase("muitas");
        assertThat(chainCalls.get()).isEqualTo(5);
    }

    @Test
    void deveRetornar429QuandoCadastroExcedeLimite() throws Exception {
        for (int i = 0; i < 5; i++) {
            filter.doFilter(cadastroRequest("10.0.0.1"), new MockHttpServletResponse(), countingChain);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(cadastroRequest("10.0.0.1"), blocked, countingChain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(chainCalls.get()).isEqualTo(5);
    }

    @Test
    void naoDeveLimitarRefresh() throws Exception {
        for (int i = 0; i < 6; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/auth/refresh", "10.0.0.1"), response, countingChain);
            assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        assertThat(chainCalls.get()).isEqualTo(6);
    }

    @Test
    void naoDeveLimitarOutrosPaths() throws Exception {
        for (int i = 0; i < 6; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(post("/tarefas", "10.0.0.1"), response, countingChain);
            assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        assertThat(chainCalls.get()).isEqualTo(6);
    }

    @Test
    void deveSeguirChainQuandoAbaixoDoLimite() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(loginRequest("10.0.0.1"), response, countingChain);

        assertThat(chainCalls.get()).isEqualTo(1);
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void naoDeveBloquearQuandoDesabilitado() throws Exception {
        AuthRateLimitFilter disabled = new AuthRateLimitFilter(new RateLimitProperties(false, 5), CLOCK);

        for (int i = 0; i < 6; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            disabled.doFilter(loginRequest("10.0.0.1"), response, countingChain);
            assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
        assertThat(chainCalls.get()).isEqualTo(6);
    }

    private static MockHttpServletRequest loginRequest(String ip) {
        return post("/auth/login", ip);
    }

    private static MockHttpServletRequest cadastroRequest(String ip) {
        return post("/usuarios", ip);
    }

    private static MockHttpServletRequest post(String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.setRemoteAddr(ip);
        return request;
    }
}
