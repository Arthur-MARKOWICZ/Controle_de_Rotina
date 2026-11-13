package com.controlerotina.habito.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.shared.exception.HabitoJaAtivoException;
import com.controlerotina.shared.exception.HabitoJaPausadoException;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;
import com.controlerotina.tag.service.TagVinculoService;

@ExtendWith(MockitoExtension.class)
class HabitoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID HABITO_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    HabitoRepository habitoRepository;
    @Mock
    HabitoStreakService habitoStreakService;
    @Mock
    TagVinculoService tagVinculoService;

    private HabitoService service;

    @BeforeEach
    void setUp() {
        service = new HabitoService(
                habitoRepository,
                habitoStreakService,
                tagVinculoService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void devePausarHabitoAtivo() {
        Habito habito = habitoAtivo();
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));
        when(tagVinculoService.listarTagsHabito(HABITO_ID)).thenReturn(List.of());

        var resposta = service.pausar(USUARIO, HABITO_ID);

        assertThat(habito.getStatus()).isEqualTo(StatusHabito.PAUSADO);
        assertThat(resposta.status()).isEqualTo(StatusHabito.PAUSADO);
    }

    @Test
    void naoDevePausarHabitoJaPausado() {
        Habito habito = habitoAtivo();
        habito.pausar(AGORA);
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.pausar(USUARIO, HABITO_ID))
                .isInstanceOf(HabitoJaPausadoException.class);
        assertThat(habito.getStatus()).isEqualTo(StatusHabito.PAUSADO);
    }

    @Test
    void naoDevePausarHabitoDeOutroUsuario() {
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pausar(USUARIO, HABITO_ID))
                .isInstanceOf(HabitoNaoEncontradoException.class);
    }

    @Test
    void naoDevePausarHabitoDeletado() {
        Habito habito = habitoAtivo();
        habito.softDelete(AGORA);
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.pausar(USUARIO, HABITO_ID))
                .isInstanceOf(HabitoNaoEncontradoException.class);
        assertThat(habito.getStatus()).isEqualTo(StatusHabito.ATIVO);
    }

    @Test
    void naoDeveReativarHabitoJaAtivo() {
        Habito habito = habitoAtivo();
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.reativar(USUARIO, HABITO_ID))
                .isInstanceOf(HabitoJaAtivoException.class);
        assertThat(habito.getStatus()).isEqualTo(StatusHabito.ATIVO);
    }

    @Test
    void naoDeveRestaurarHabitoAindaAtivo() {
        Habito habito = habitoAtivo();
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.of(habito));

        assertThatThrownBy(() -> service.restaurar(USUARIO, HABITO_ID))
                .isInstanceOf(HabitoNaoEncontradoException.class);
        assertThat(habito.isDeletado()).isFalse();
    }

    @Test
    void naoDeveExcluirHabitoDeOutroUsuario() {
        when(habitoRepository.findByIdAndUsuarioId(HABITO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.excluir(USUARIO, HABITO_ID))
                .isInstanceOf(HabitoNaoEncontradoException.class);
        verify(habitoRepository, never()).save(any());
    }

    private static Habito habitoAtivo() {
        return new Habito(
                HABITO_ID, USUARIO, "Correr", null, 30,
                Set.of(DiaSemana.TERCA), null, StatusHabito.ATIVO, 0, 0, AGORA, AGORA);
    }
}
