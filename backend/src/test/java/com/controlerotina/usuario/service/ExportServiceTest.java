package com.controlerotina.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.controlerotina.shared.exception.CredenciaisInvalidasException;
import com.controlerotina.usuario.repository.UsuarioRepository;

import jakarta.persistence.EntityManager;

class ExportServiceTest {

    private final EntityManager em = mock(EntityManager.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

    private ExportService servico(String instante) {
        return new ExportService(em, usuarioRepository, Clock.fixed(Instant.parse(instante), ZoneOffset.UTC));
    }

    @Test
    void dataDoArquivoDeveUsarODiaEmBrt() {
        assertThat(servico("2026-11-24T15:00:00Z").dataDoArquivo()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void dataDoArquivoUmMinutoAntesDaMeiaNoiteBrtAindaEDiaAnterior() {
        assertThat(servico("2026-11-25T02:59:00Z").dataDoArquivo()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void dataDoArquivoNaMeiaNoiteBrtViraODia() {
        assertThat(servico("2026-11-25T03:00:00Z").dataDoArquivo()).isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void dataDoArquivoNaViradaDoAnoEmBrt() {
        assertThat(servico("2027-01-01T02:59:59Z").dataDoArquivo()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void exportarUsuarioInexistenteDeveFalharSemConsultarDominios() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico("2026-11-24T15:00:00Z").exportar(id))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(em, never()).createQuery(anyString(), org.mockito.ArgumentMatchers.<Class<Object>>any());
    }

    @Test
    void exportarComIdNuloDeveFalharAntesDeConsultar() {
        assertThatThrownBy(() -> servico("2026-11-24T15:00:00Z").exportar(null))
                .isInstanceOf(NullPointerException.class);
        verify(usuarioRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void falhaDoRepositorioDevePropagarSemRespostaParcial() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenThrow(new IllegalStateException("banco fora"));

        assertThatThrownBy(() -> servico("2026-11-24T15:00:00Z").exportar(id))
                .isInstanceOf(IllegalStateException.class);
    }
}
