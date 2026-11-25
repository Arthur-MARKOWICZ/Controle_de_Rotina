package com.controlerotina.auth.jwt;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.controlerotina.auth.service.AccessTokenRevogacaoService;
import com.controlerotina.usuario.repository.UsuarioRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Marcado no request quando o JWT é válido mas a conta não existe mais. O entry point do
     * {@code SecurityConfig} lê o atributo para responder 401 "Conta excluída".
     */
    public static final String ATRIBUTO_CONTA_EXCLUIDA = JwtAuthenticationFilter.class.getName() + ".contaExcluida";

    /** `title` do 401 de conta excluída: os clientes (web, Android, MCP) comparam com este texto exato. */
    public static final String TITULO_CONTA_EXCLUIDA = "Conta excluída";

    public static final String DETALHE_CONTA_EXCLUIDA = "Esta conta foi excluída.";

    private final JwtService jwtService;
    private final AccessTokenRevogacaoService accessTokenRevogacaoService;
    private final UsuarioRepository usuarioRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AccessTokenRevogacaoService accessTokenRevogacaoService,
            UsuarioRepository usuarioRepository
    ) {
        this.jwtService = jwtService;
        this.accessTokenRevogacaoService = accessTokenRevogacaoService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length()).trim();
                jwtService.extrairClaims(token).ifPresent(claims -> {
                    if (accessTokenRevogacaoService.estaRevogado(claims.jti())) {
                        return;
                    }
                    // Um JWT nosso só existe para um usuário que já existiu: sub ausente = conta excluída.
                    if (!usuarioRepository.existsById(claims.usuarioId())) {
                        request.setAttribute(ATRIBUTO_CONTA_EXCLUIDA, Boolean.TRUE);
                        return;
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(
                            claims.usuarioId(),
                            null,
                            List.of()
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }
        filterChain.doFilter(request, response);
    }
}
