package com.controlerotina.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.controlerotina.usuario.dto.AtualizarMeRequest;
import com.controlerotina.usuario.dto.CadastroUsuarioRequest;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-29T15:30:00Z");

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() {
        usuarioService = new UsuarioService(
                usuarioRepository,
                passwordEncoder,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void cadastrarDeveGravarCreatedAtEUpdatedAtComClock() {
        when(usuarioRepository.existsByEmail("ana@email.com")).thenReturn(false);
        when(passwordEncoder.encode("Senha@123")).thenReturn("hash");
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        usuarioService.cadastrar(new CadastroUsuarioRequest("Ana", "ana@email.com", "Senha@123"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario salvo = captor.getValue();

        assertThat(salvo.getCreatedAt()).isEqualTo(AGORA);
        assertThat(salvo.getUpdatedAt()).isEqualTo(AGORA);
    }

    @Test
    void atualizarMeDeveGravarUpdatedAtComClock() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant criadoEm = Instant.parse("2026-01-01T00:00:00Z");
        Usuario existente = new Usuario(id, "ana@email.com", "hash-atual", "Ana", criadoEm, criadoEm);

        when(usuarioRepository.findById(id)).thenReturn(Optional.of(existente));
        when(passwordEncoder.matches("Senha@123", "hash-atual")).thenReturn(true);

        usuarioService.atualizarMe(id, new AtualizarMeRequest("Senha@123", "Ana Silva", null, null));

        assertThat(existente.getUpdatedAt()).isEqualTo(AGORA);
        assertThat(existente.getCreatedAt()).isEqualTo(criadoEm);
        assertThat(existente.getNome()).isEqualTo("Ana Silva");
    }
}
