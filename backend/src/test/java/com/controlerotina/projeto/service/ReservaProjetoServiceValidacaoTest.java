package com.controlerotina.projeto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.capacidade.repository.CapacidadeSemanalRepository;
import com.controlerotina.planner.service.PropostaGeracaoService;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.projeto.repository.ReservaProjetoSemanalRepository;
import com.controlerotina.shared.exception.ReservaCapacidadeInsuficienteException;

@ExtendWith(MockitoExtension.class)
class ReservaProjetoServiceValidacaoTest {

    private static final Instant AGORA = Instant.parse("2026-10-29T15:00:00Z");
    private static final UUID USUARIO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    ReservaProjetoSemanalRepository reservaRepository;

    @Mock
    ProjetoRepository projetoRepository;

    @Mock
    CapacidadeSemanalRepository capacidadeSemanalRepository;

    @Mock
    PropostaGeracaoService propostaGeracaoService;

    private ReservaProjetoService service;

    @BeforeEach
    void setUp() {
        service = new ReservaProjetoService(
                reservaRepository,
                projetoRepository,
                capacidadeSemanalRepository,
                propostaGeracaoService,
                Clock.fixed(AGORA, ZoneOffset.UTC)
        );
    }

    @Test
    void t4_deveRejeitarQuandoSomaReservasExcedeHorasDoTemplate() {
        CapacidadeSemanal template = new CapacidadeSemanal(
                UUID.randomUUID(),
                USUARIO_ID,
                DiaSemana.SEGUNDA,
                2,
                true,
                AGORA,
                AGORA
        );
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO_ID, DiaSemana.SEGUNDA))
                .thenReturn(Optional.of(template));

        ReservaProjetoSemanal existente = new ReservaProjetoSemanal(
                UUID.randomUUID(),
                USUARIO_ID,
                UUID.randomUUID(),
                DiaSemana.SEGUNDA,
                90,
                AGORA,
                AGORA
        );
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO_ID, DiaSemana.SEGUNDA))
                .thenReturn(List.of(existente));

        assertThatThrownBy(() -> service.validarSomaReservas(USUARIO_ID, DiaSemana.SEGUNDA, 60, null))
                .isInstanceOf(ReservaCapacidadeInsuficienteException.class);
    }

    @Test
    void t4_deveRejeitarQuandoNaoHaTemplateAtivo() {
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO_ID, DiaSemana.TERCA))
                .thenReturn(Optional.empty());
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO_ID, DiaSemana.TERCA))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.validarSomaReservas(USUARIO_ID, DiaSemana.TERCA, 15, null))
                .isInstanceOf(ReservaCapacidadeInsuficienteException.class)
                .satisfies(ex -> {
                    ReservaCapacidadeInsuficienteException erro = (ReservaCapacidadeInsuficienteException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Capacidade insuficiente");
                });
    }

    @Test
    void deveAceitarQuandoSomaCabeNoTemplate() {
        CapacidadeSemanal template = template(DiaSemana.QUARTA, 2);
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO_ID, DiaSemana.QUARTA))
                .thenReturn(Optional.of(template));
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO_ID, DiaSemana.QUARTA))
                .thenReturn(List.of(reserva(60, DiaSemana.QUARTA)));

        service.validarSomaReservas(USUARIO_ID, DiaSemana.QUARTA, 60, null);
    }

    @Test
    void deveAceitarQuandoSomaIgualaACapacidade() {
        CapacidadeSemanal template = template(DiaSemana.QUINTA, 1);
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO_ID, DiaSemana.QUINTA))
                .thenReturn(Optional.of(template));
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO_ID, DiaSemana.QUINTA))
                .thenReturn(List.of());

        service.validarSomaReservas(USUARIO_ID, DiaSemana.QUINTA, 60, null);
    }

    @Test
    void aoAtualizarDeveIgnorarAPropriaReservaNaSoma() {
        UUID propria = UUID.randomUUID();
        CapacidadeSemanal template = template(DiaSemana.SEXTA, 1);
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO_ID, DiaSemana.SEXTA))
                .thenReturn(Optional.of(template));
        ReservaProjetoSemanal existente = new ReservaProjetoSemanal(
                propria, USUARIO_ID, UUID.randomUUID(), DiaSemana.SEXTA, 60, AGORA, AGORA);
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO_ID, DiaSemana.SEXTA))
                .thenReturn(List.of(existente));

        service.validarSomaReservas(USUARIO_ID, DiaSemana.SEXTA, 60, propria);
    }

    @Test
    void minutosZeroNaoDevemEstourarCapacidadeVazia() {
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO_ID, DiaSemana.SABADO))
                .thenReturn(Optional.empty());
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO_ID, DiaSemana.SABADO))
                .thenReturn(List.of());

        service.validarSomaReservas(USUARIO_ID, DiaSemana.SABADO, 0, null);
    }

    @Test
    void excederPorUmMinutoDeveLancar409() {
        CapacidadeSemanal template = template(DiaSemana.DOMINGO, 1);
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO_ID, DiaSemana.DOMINGO))
                .thenReturn(Optional.of(template));
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO_ID, DiaSemana.DOMINGO))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.validarSomaReservas(USUARIO_ID, DiaSemana.DOMINGO, 61, null))
                .isInstanceOf(ReservaCapacidadeInsuficienteException.class)
                .satisfies(ex -> {
                    ReservaCapacidadeInsuficienteException erro = (ReservaCapacidadeInsuficienteException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Capacidade insuficiente");
                });
    }

    private CapacidadeSemanal template(DiaSemana dia, int horas) {
        return new CapacidadeSemanal(UUID.randomUUID(), USUARIO_ID, dia, horas, true, AGORA, AGORA);
    }

    private ReservaProjetoSemanal reserva(int minutos, DiaSemana dia) {
        return new ReservaProjetoSemanal(
                UUID.randomUUID(), USUARIO_ID, UUID.randomUUID(), dia, minutos, AGORA, AGORA);
    }
}
