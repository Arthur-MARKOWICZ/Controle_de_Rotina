package com.controlerotina.projeto.service;

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
import com.controlerotina.projeto.dto.CriarReservaProjetoRequest;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.projeto.repository.ReservaProjetoSemanalRepository;
import com.controlerotina.shared.exception.ProjetoArquivadoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;
import com.controlerotina.shared.exception.ReservaCapacidadeInsuficienteException;
import com.controlerotina.shared.exception.ReservaProjetoDuplicadaException;

@ExtendWith(MockitoExtension.class)
class ReservaProjetoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-27T15:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PROJETO_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

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
    void deveCriarReservaQuandoCabeNaCapacidade() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO))
                .thenReturn(Optional.of(projeto(StatusProjeto.ATIVO)));
        when(reservaRepository.existsByUsuarioIdAndProjetoIdAndDiaSemana(USUARIO, PROJETO_ID, DiaSemana.SEGUNDA))
                .thenReturn(false);
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO, DiaSemana.SEGUNDA))
                .thenReturn(Optional.of(template(2)));
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO, DiaSemana.SEGUNDA)).thenReturn(List.of());
        when(reservaRepository.save(any(ReservaProjetoSemanal.class))).thenAnswer(inv -> inv.getArgument(0));

        var criada = service.criar(USUARIO, PROJETO_ID, new CriarReservaProjetoRequest(DiaSemana.SEGUNDA, 60));

        assertThat(criada.projetoId()).isEqualTo(PROJETO_ID);
        assertThat(criada.minutosReservados()).isEqualTo(60);
        verify(reservaRepository).save(any(ReservaProjetoSemanal.class));
        verify(propostaGeracaoService).gerarSeDiaSemanaAprovado(USUARIO, DiaSemana.SEGUNDA);
    }

    @Test
    void listarSemReservasDevolveListaVazia() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO))
                .thenReturn(Optional.of(projeto(StatusProjeto.ATIVO)));
        when(reservaRepository.findByUsuarioIdAndProjetoIdOrderByDiaSemanaAsc(USUARIO, PROJETO_ID))
                .thenReturn(List.of());

        assertThat(service.listarDoProjeto(USUARIO, PROJETO_ID)).isEmpty();
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void naoCriaReservaDuplicada() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO))
                .thenReturn(Optional.of(projeto(StatusProjeto.ATIVO)));
        when(reservaRepository.existsByUsuarioIdAndProjetoIdAndDiaSemana(USUARIO, PROJETO_ID, DiaSemana.TERCA))
                .thenReturn(true);

        assertThatThrownBy(() -> service.criar(
                USUARIO, PROJETO_ID, new CriarReservaProjetoRequest(DiaSemana.TERCA, 30)))
                .isInstanceOf(ReservaProjetoDuplicadaException.class)
                .satisfies(ex -> {
                    ReservaProjetoDuplicadaException erro = (ReservaProjetoDuplicadaException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Reserva já existe");
                });
        verify(reservaRepository, never()).save(any());
        verify(propostaGeracaoService, never()).gerarSeDiaSemanaAprovado(any(), any());
    }

    @Test
    void naoCriaReservaEmProjetoArquivado() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO))
                .thenReturn(Optional.of(projeto(StatusProjeto.ARQUIVADO)));

        assertThatThrownBy(() -> service.criar(
                USUARIO, PROJETO_ID, new CriarReservaProjetoRequest(DiaSemana.QUARTA, 30)))
                .isInstanceOf(ProjetoArquivadoException.class)
                .satisfies(ex -> {
                    ProjetoArquivadoException erro = (ProjetoArquivadoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Projeto arquivado");
                });
        verify(reservaRepository, never()).existsByUsuarioIdAndProjetoIdAndDiaSemana(any(), any(), any());
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void naoCriaReservaDeProjetoDeOutroUsuario() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(
                USUARIO, PROJETO_ID, new CriarReservaProjetoRequest(DiaSemana.QUINTA, 30)))
                .isInstanceOf(ProjetoNaoEncontradoException.class)
                .satisfies(ex -> {
                    ProjetoNaoEncontradoException erro = (ProjetoNaoEncontradoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Projeto não encontrado");
                });
        verify(reservaRepository, never()).save(any());
    }

    @Test
    void naoCriaQuandoCapacidadeInsuficiente() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO))
                .thenReturn(Optional.of(projeto(StatusProjeto.ATIVO)));
        when(reservaRepository.existsByUsuarioIdAndProjetoIdAndDiaSemana(USUARIO, PROJETO_ID, DiaSemana.SEXTA))
                .thenReturn(false);
        when(capacidadeSemanalRepository.findByUsuarioIdAndDiaSemanaAndAtivoTrue(USUARIO, DiaSemana.SEXTA))
                .thenReturn(Optional.of(template(1)));
        when(reservaRepository.findByUsuarioIdAndDiaSemana(USUARIO, DiaSemana.SEXTA)).thenReturn(List.of());

        assertThatThrownBy(() -> service.criar(
                USUARIO, PROJETO_ID, new CriarReservaProjetoRequest(DiaSemana.SEXTA, 90)))
                .isInstanceOf(ReservaCapacidadeInsuficienteException.class)
                .satisfies(ex -> {
                    ReservaCapacidadeInsuficienteException erro = (ReservaCapacidadeInsuficienteException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(409);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Capacidade insuficiente");
                });
        verify(reservaRepository, never()).save(any());
        verify(propostaGeracaoService, never()).gerarSeDiaSemanaAprovado(any(), any());
    }

    @Test
    void listarProjetoInexistenteNaoConsultaReservas() {
        when(projetoRepository.findByIdAndUsuarioId(PROJETO_ID, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarDoProjeto(USUARIO, PROJETO_ID))
                .isInstanceOf(ProjetoNaoEncontradoException.class)
                .satisfies(ex -> {
                    ProjetoNaoEncontradoException erro = (ProjetoNaoEncontradoException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Projeto não encontrado");
                });
        verify(reservaRepository, never()).findByUsuarioIdAndProjetoIdOrderByDiaSemanaAsc(any(), any());
    }

    private Projeto projeto(StatusProjeto status) {
        return new Projeto(PROJETO_ID, USUARIO, "Casa", null, status, AGORA, AGORA);
    }

    private CapacidadeSemanal template(int horas) {
        return new CapacidadeSemanal(
                UUID.randomUUID(), USUARIO, DiaSemana.SEGUNDA, horas, true, AGORA, AGORA);
    }
}
