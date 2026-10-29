package com.controlerotina.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.controlerotina.auth.dto.LoginRequest;
import com.controlerotina.auth.jwt.JwtService;
import com.controlerotina.shared.exception.CredenciaisInvalidasException;
import com.controlerotina.usuario.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    RefreshTokenService refreshTokenService;

    @InjectMocks
    AuthService authService;

    @Test
    void deveCompararSenhaComHashFicticioQuandoEmailNaoExiste() {
        when(usuarioRepository.findByEmail("ausente@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("Senha@123"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ausente@email.com", "Senha@123")))
                .isInstanceOf(CredenciaisInvalidasException.class);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).matches(eq("Senha@123"), hashCaptor.capture());
        assertThat(hashCaptor.getValue()).startsWith("$2");
        assertThat(hashCaptor.getValue()).hasSizeGreaterThan(50);
    }
}
