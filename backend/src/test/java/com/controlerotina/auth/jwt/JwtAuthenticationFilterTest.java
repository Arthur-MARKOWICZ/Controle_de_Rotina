package com.controlerotina.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.controlerotina.auth.service.AccessTokenRevogacaoService;
import com.controlerotina.usuario.repository.UsuarioRepository;

import jakarta.servlet.FilterChain;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final UUID USUARIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant EXPIRA = Instant.parse("2026-10-29T12:15:00Z");

    @Mock
    JwtService jwtService;

    @Mock
    AccessTokenRevogacaoService accessTokenRevogacaoService;

    @Mock
    UsuarioRepository usuarioRepository;

    private JwtAuthenticationFilter filter;
    private AtomicInteger chainCalls;
    private FilterChain countingChain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService, accessTokenRevogacaoService, usuarioRepository);
        chainCalls = new AtomicInteger();
        countingChain = (request, response) -> chainCalls.incrementAndGet();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveAutenticarQuandoTokenValido() throws Exception {
        when(jwtService.extrairClaims("jwt-ok")).thenReturn(Optional.of(claims()));
        when(accessTokenRevogacaoService.estaRevogado("jti-1")).thenReturn(false);
        when(usuarioRepository.existsById(USUARIO_ID)).thenReturn(true);
        MockHttpServletRequest request = comBearer("jwt-ok");

        filter.doFilter(request, new MockHttpServletResponse(), countingChain);

        assertThat(request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_CONTA_EXCLUIDA)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(USUARIO_ID);
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void naoDeveAutenticarQuandoNaoHaHeaderAuthorization() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), countingChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).extrairClaims(anyString());
        verify(usuarioRepository, never()).existsById(any());
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void naoDeveAutenticarQuandoBearerMalformado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer");

        filter.doFilter(request, new MockHttpServletResponse(), countingChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).extrairClaims(anyString());
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void naoDeveAutenticarQuandoEsquemaNaoEBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc");

        filter.doFilter(request, new MockHttpServletResponse(), countingChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtService, never()).extrairClaims(anyString());
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void naoDeveAutenticarQuandoTokenExpirado() throws Exception {
        when(jwtService.extrairClaims("jwt-expirado")).thenReturn(Optional.empty());

        filter.doFilter(comBearer("jwt-expirado"), new MockHttpServletResponse(), countingChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(accessTokenRevogacaoService, never()).estaRevogado(anyString());
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void naoDeveAutenticarQuandoAssinaturaInvalida() throws Exception {
        when(jwtService.extrairClaims("jwt-assinatura")).thenReturn(Optional.empty());

        filter.doFilter(comBearer("jwt-assinatura"), new MockHttpServletResponse(), countingChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void naoDeveAutenticarQuandoTokenRevogado() throws Exception {
        when(jwtService.extrairClaims("jwt-revogado")).thenReturn(Optional.of(claims()));
        when(accessTokenRevogacaoService.estaRevogado("jti-1")).thenReturn(true);

        filter.doFilter(comBearer("jwt-revogado"), new MockHttpServletResponse(), countingChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void naoDeveSobrescreverAutenticacaoJaPresente() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ja-autenticado", null)
        );

        filter.doFilter(comBearer("jwt-ok"), new MockHttpServletResponse(), countingChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo("ja-autenticado");
        verify(jwtService, never()).extrairClaims(anyString());
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void naoDeveAutenticarEDeveMarcarContaExcluidaQuandoUsuarioNaoExisteMais() throws Exception {
        when(jwtService.extrairClaims("jwt-orfao")).thenReturn(Optional.of(claims()));
        when(accessTokenRevogacaoService.estaRevogado("jti-1")).thenReturn(false);
        when(usuarioRepository.existsById(USUARIO_ID)).thenReturn(false);
        MockHttpServletRequest request = comBearer("jwt-orfao");

        filter.doFilter(request, new MockHttpServletResponse(), countingChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_CONTA_EXCLUIDA)).isEqualTo(Boolean.TRUE);
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    @Test
    void tokenRevogadoNaoDeveConsultarUsuarioNemMarcarContaExcluida() throws Exception {
        when(jwtService.extrairClaims("jwt-revogado")).thenReturn(Optional.of(claims()));
        when(accessTokenRevogacaoService.estaRevogado("jti-1")).thenReturn(true);
        MockHttpServletRequest request = comBearer("jwt-revogado");

        filter.doFilter(request, new MockHttpServletResponse(), countingChain);

        verify(usuarioRepository, never()).existsById(any());
        assertThat(request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_CONTA_EXCLUIDA)).isNull();
    }

    @Test
    void tokenExpiradoNaoDeveMarcarContaExcluida() throws Exception {
        when(jwtService.extrairClaims("jwt-expirado")).thenReturn(Optional.empty());
        MockHttpServletRequest request = comBearer("jwt-expirado");

        filter.doFilter(request, new MockHttpServletResponse(), countingChain);

        verify(usuarioRepository, never()).existsById(any());
        assertThat(request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_CONTA_EXCLUIDA)).isNull();
    }

    private static JwtService.AccessClaims claims() {
        return new JwtService.AccessClaims(USUARIO_ID, "jti-1", EXPIRA);
    }

    private static MockHttpServletRequest comBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }
}
