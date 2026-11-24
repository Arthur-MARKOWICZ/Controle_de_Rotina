package com.controlerotina.objetivo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.ErrorResponseException;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.CumprimentoHabitoLinha;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.execucao.repository.MinutoTarefaLinha;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.objetivo.dto.ObjetivoEvolucaoResponse;
import com.controlerotina.objetivo.dto.ObjetivoProgressoResponse;
import com.controlerotina.objetivo.dto.OrigemVinculoObjetivo;
import com.controlerotina.objetivo.dto.PontoEvolucaoResponse;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.repository.ObjetivoRepository;
import com.controlerotina.shared.exception.ObjetivoNaoEncontradoException;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
class ObjetivoEvolucaoServiceTest {

    /** Quarta 18/11/2026 12:00 em BRT. */
    private static final Instant AGORA = Instant.parse("2026-11-18T15:00:00Z");
    private static final Instant ANTES = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OUTRO_USUARIO = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID OBJETIVO_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TAREFA_A = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TAREFA_B = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID TAREFA_C = UUID.fromString("12121212-1212-1212-1212-121212121212");
    private static final UUID FILHO_A = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID HABITO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    ObjetivoRepository objetivoRepository;
    @Mock
    ObjetivoVinculoService objetivoVinculoService;
    @Mock
    TarefaRepository tarefaRepository;
    @Mock
    ExecucaoRepository execucaoRepository;

    private ObjetivoEvolucaoService service;

    @BeforeEach
    void setUp() {
        service = serviceCom(AGORA);
    }

    // --- caminho feliz -------------------------------------------------------------------------

    @Test
    void deveAcumularDoisDiasDeTrabalhoEmTarefaParcial() {
        stubLinhas(linha(TAREFA_A, dia(3), 30), linha(TAREFA_A, dia(5), 45));

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 120))));

        assertThat(evolucao.antesDoHistorico()).isZero();
        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(dia(3), 30, 30),
                new PontoEvolucaoResponse(dia(5), 45, 75));
    }

    @Test
    void deveSomarMinutosDoMesmoDiaDeItensDiferentes() {
        stubLinhas(linha(TAREFA_A, dia(3), 30), linha(TAREFA_B, dia(3), 60));

        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(), conjunto(
                raiz(tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 120)),
                raiz(tarefa(TAREFA_B, StatusTarefa.EM_PROGRESSO, 90))));

        assertThat(evolucao.pontos()).containsExactly(new PontoEvolucaoResponse(dia(3), 90, 90));
    }

    // --- tarefa --------------------------------------------------------------------------------

    @Test
    void deveColocarOSaldoDaTarefaFinalizadaNoDiaDaFinalizacao() {
        stubLinhas(linha(TAREFA_A, dia(3), 30));

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(finalizada(TAREFA_A, 120, instanteBrt(dia(6))))));

        assertThat(evolucao.antesDoHistorico()).isZero();
        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(dia(3), 30, 30),
                new PontoEvolucaoResponse(dia(6), 90, 120));
    }

    @Test
    void naoDeveSomarSaldoQuandoAExecucaoJaCompletouADuracao() {
        stubLinhas(linha(TAREFA_A, dia(5), 60));

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(finalizada(TAREFA_A, 60, instanteBrt(dia(5))))));

        assertThat(evolucao.pontos()).containsExactly(new PontoEvolucaoResponse(dia(5), 60, 60));
    }

    @Test
    void deveLimitarExecucoesAcimaDaDuracaoDescartandoOExcesso() {
        stubLinhas(linha(TAREFA_A, dia(3), 20), linha(TAREFA_A, dia(4), 25));

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 30))));

        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(dia(3), 20, 20),
                new PontoEvolucaoResponse(dia(4), 10, 30));
    }

    @Test
    void deveContarExecucaoDeSubtarefaNaRaizNoDiaDaExecucao() {
        Tarefa filho = tarefa(FILHO_A, StatusTarefa.EM_PROGRESSO, 30);
        filho.definirParentId(TAREFA_A);
        when(tarefaRepository.findByParentIdInAndDeletedAtIsNull(any())).thenReturn(List.of(filho));
        stubLinhas(linha(FILHO_A, dia(4), 30));

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 60))));

        assertThat(evolucao.pontos()).containsExactly(new PontoEvolucaoResponse(dia(4), 30, 30));
    }

    @Test
    void naoDeveSomarExecucaoPosteriorAoSaldoDaFinalizacao() {
        stubLinhas(linha(TAREFA_A, dia(3), 30), linha(TAREFA_A, dia(8), 30));

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(finalizada(TAREFA_A, 60, instanteBrt(dia(6))))));

        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(dia(3), 30, 30),
                new PontoEvolucaoResponse(dia(6), 30, 60));
    }

    @Test
    void deveUsarADataDoPlanoQuandoAExecucaoNaoTemInstanteDeConclusao() {
        stubLinhas(new MinutoTarefaLinha(TAREFA_A, null, dia(4), 30));

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 60))));

        assertThat(evolucao.pontos()).containsExactly(new PontoEvolucaoResponse(dia(4), 30, 30));
    }

    // --- antes do histórico --------------------------------------------------------------------

    @Test
    void deveColocarOSaldoSemDataAntesDoHistoricoMantendoExecucoesNosDias() {
        stubLinhas(linha(TAREFA_A, LocalDate.of(2026, 10, 18), 30));

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.FINALIZADA, 90))));

        assertThat(evolucao.antesDoHistorico()).isEqualTo(60);
        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(LocalDate.of(2026, 10, 18), 30, 90));
    }

    @Test
    void deveDevolverSoOTotalAntesDoHistoricoQuandoNaoHaExecucoesNemDatas() {
        stubLinhas();

        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(), conjunto(
                raiz(tarefa(TAREFA_A, StatusTarefa.FINALIZADA, 60)),
                raiz(tarefa(TAREFA_B, StatusTarefa.FINALIZADA, 30))));

        assertThat(evolucao.antesDoHistorico()).isEqualTo(90);
        assertThat(evolucao.pontos()).isEmpty();
    }

    // --- itens que não contam ------------------------------------------------------------------

    @Test
    void naoDeveContarTarefaCanceladaNemConsultarExecucoes() {
        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.CANCELADA, 60))));

        assertThat(evolucao).isEqualTo(new ObjetivoEvolucaoResponse(0, List.of()));
        verify(execucaoRepository, never()).listarMinutosConcluidosPorTarefas(any(), any(), any());
    }

    @Test
    void naoDeveContarTarefaComSoftDelete() {
        Tarefa apagada = tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 60);
        apagada.softDelete(AGORA);

        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(), conjunto(raiz(apagada)));

        assertThat(evolucao).isEqualTo(new ObjetivoEvolucaoResponse(0, List.of()));
        verify(execucaoRepository, never()).listarMinutosConcluidosPorTarefas(any(), any(), any());
    }

    @Test
    void deveDevolverVazioParaObjetivoSemItens() {
        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(), ObjetivoConjunto.vazio());

        assertThat(evolucao).isEqualTo(new ObjetivoEvolucaoResponse(0, List.of()));
        verify(execucaoRepository, never()).listarMinutosConcluidosPorTarefas(any(), any(), any());
        verify(execucaoRepository, never()).listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any());
    }

    @Test
    void naoDeveContarHabitoPausadoNemConsultarCumprimentos() {
        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(),
                conjuntoHabito(habito(StatusHabito.PAUSADO, 30, todosOsDias()), instanteBrt(dia(2))));

        assertThat(evolucao).isEqualTo(new ObjetivoEvolucaoResponse(0, List.of()));
        verify(execucaoRepository, never()).listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any());
    }

    @Test
    void deveVoltarACurvaDoHabitoAoReativarSemPerderOsDias() {
        Habito habito = habito(StatusHabito.ATIVO, 30, todosOsDias());
        habito.pausar(AGORA);
        ObjetivoConjunto conjunto = conjuntoHabito(habito, instanteBrt(dia(16)));
        assertThat(service.calcular(objetivo(), conjunto).pontos()).isEmpty();

        habito.reativar(AGORA);
        stubCumprimento(dia(16), 30);

        assertThat(service.calcular(objetivo(), conjunto).pontos())
                .containsExactly(new PontoEvolucaoResponse(dia(16), 30, 30));
    }

    // --- hábito --------------------------------------------------------------------------------

    @Test
    void deveSomarExecucoesDoHabitoDentroDaJanela() {
        when(execucaoRepository.listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cumprimento(dia(2), 30), cumprimento(dia(4), 30)));

        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(),
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), instanteBrt(dia(2))));

        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(dia(2), 30, 30),
                new PontoEvolucaoResponse(dia(4), 30, 60));
    }

    @Test
    void naoDeveSomarExecucaoDoHabitoAnteriorAoVinculo() {
        when(execucaoRepository.listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cumprimento(dia(3), 30), cumprimento(dia(6), 30)));

        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(),
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), instanteBrt(dia(5))));

        assertThat(evolucao.pontos()).containsExactly(new PontoEvolucaoResponse(dia(6), 30, 30));
    }

    @Test
    void deveLimitarOHabitoAoTotalDaJanelaDescartandoOExcesso() {
        Instant terca17 = Instant.parse("2026-11-17T15:00:00Z");
        ObjetivoEvolucaoService naTerca = serviceCom(terca17);
        when(execucaoRepository.listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cumprimento(dia(16), 40), cumprimento(dia(17), 40)));

        ObjetivoEvolucaoResponse evolucao = naTerca.calcular(objetivo(), conjuntoHabito(
                habito(StatusHabito.ATIVO, 30, Set.of(DiaSemana.SEGUNDA, DiaSemana.TERCA)), instanteBrt(dia(16))));

        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(dia(16), 40, 40),
                new PontoEvolucaoResponse(dia(17), 20, 60));
    }

    @Test
    void naoDeveContarHabitoSoftDeleted() {
        Habito habito = habito(StatusHabito.ATIVO, 30, todosOsDias());
        habito.softDelete(AGORA);

        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(), conjuntoHabito(habito, instanteBrt(dia(2))));

        assertThat(evolucao).isEqualTo(new ObjetivoEvolucaoResponse(0, List.of()));
    }

    // --- virada de mês e meia-noite em BRT -----------------------------------------------------

    @Test
    void deveColocarExecucaoDas2330BrtNoDiaAnteriorNaViradaDeMes() {
        Instant doisEMeiaUtc = Instant.parse("2026-12-01T02:30:00Z");
        stubLinhas(new MinutoTarefaLinha(TAREFA_A, doisEMeiaUtc, LocalDate.of(2026, 11, 30), 30));

        ObjetivoEvolucaoResponse evolucao = serviceCom(Instant.parse("2026-12-05T15:00:00Z")).calcular(
                objetivo(), conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 60))));

        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(LocalDate.of(2026, 11, 30), 30, 30));
    }

    @Test
    void deveColocarFinalizacaoDas2359BrtNoUltimoDiaDoMes() {
        Instant finalizadaEm = Instant.parse("2026-11-01T02:59:00Z");
        stubLinhas();

        ObjetivoEvolucaoResponse evolucao = service.calcular(
                objetivo(), conjunto(raiz(finalizada(TAREFA_A, 60, finalizadaEm))));

        assertThat(evolucao.pontos()).containsExactly(
                new PontoEvolucaoResponse(LocalDate.of(2026, 10, 31), 60, 60));
    }

    @Test
    void deveOrdenarPontosAtravessandoAViradaDeMes() {
        stubLinhas(
                new MinutoTarefaLinha(TAREFA_A, Instant.parse("2026-12-01T12:00:00Z"), LocalDate.of(2026, 12, 1), 15),
                new MinutoTarefaLinha(TAREFA_A, Instant.parse("2026-11-30T12:00:00Z"), LocalDate.of(2026, 11, 30), 15));

        ObjetivoEvolucaoResponse evolucao = serviceCom(Instant.parse("2026-12-05T15:00:00Z")).calcular(
                objetivo(), conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 60))));

        assertThat(evolucao.pontos()).extracting(PontoEvolucaoResponse::data)
                .containsExactly(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 1));
    }

    // --- invariante com o progresso ------------------------------------------------------------

    @Test
    void ultimoAcumuladoDeveSerIgualAoProgressoNumConjuntoMisto() {
        Tarefa parcial = tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 120);
        Tarefa comData = finalizada(TAREFA_B, 90, instanteBrt(dia(9)));
        Tarefa semData = tarefa(TAREFA_C, StatusTarefa.FINALIZADA, 45);
        Tarefa cancelada = tarefa(UUID.randomUUID(), StatusTarefa.CANCELADA, 60);
        Tarefa filho = tarefa(FILHO_A, StatusTarefa.EM_PROGRESSO, 30);
        filho.definirParentId(TAREFA_A);
        List<MinutoTarefaLinha> linhas = List.of(
                linha(TAREFA_A, dia(2), 30),
                linha(FILHO_A, dia(3), 60),
                linha(TAREFA_A, dia(7), 60),
                linha(TAREFA_B, dia(4), 30),
                linha(TAREFA_C, dia(5), 15));
        stubTudoCoerente(linhas, List.of(filho));
        lenient().when(execucaoRepository.listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cumprimento(dia(12), 30), cumprimento(dia(13), 30), cumprimento(dia(14), 30)));
        ObjetivoConjunto conjunto = new ObjetivoConjunto(
                List.of(raiz(parcial), raiz(comData), raiz(semData), raiz(cancelada)),
                List.of(new ObjetivoConjunto.HabitoVinculado(
                        habito(StatusHabito.ATIVO, 30, todosOsDias()), instanteBrt(dia(11)))),
                List.of());

        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(), conjunto);
        ObjetivoProgressoResponse progresso = progressoCom(AGORA).calcular(objetivo(), conjunto);

        assertThat(progresso).isNotNull();
        int ultimo = evolucao.pontos().isEmpty()
                ? evolucao.antesDoHistorico()
                : evolucao.pontos().get(evolucao.pontos().size() - 1).minutosAcumulados();
        assertThat(ultimo).isEqualTo(progresso.minutosConcluidos());
        assertThat(evolucao.antesDoHistorico()).isEqualTo(30);
    }

    @Test
    void ultimoAcumuladoDeveSerIgualAoProgressoComExecucoesAcimaDaDuracao() {
        Tarefa pequena = tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 30);
        stubTudoCoerente(List.of(linha(TAREFA_A, dia(3), 20), linha(TAREFA_A, dia(4), 25)), List.of());
        ObjetivoConjunto conjunto = conjunto(raiz(pequena));

        ObjetivoEvolucaoResponse evolucao = service.calcular(objetivo(), conjunto);
        ObjetivoProgressoResponse progresso = progressoCom(AGORA).calcular(objetivo(), conjunto);

        assertThat(evolucao.pontos().get(evolucao.pontos().size() - 1).minutosAcumulados())
                .isEqualTo(progresso.minutosConcluidos())
                .isEqualTo(30);
    }

    // --- obter: dono e 404 ---------------------------------------------------------------------

    @Test
    void deveRetornar404ParaObjetivoDeOutroUsuarioSemTocarNosItens() {
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_ID, OUTRO_USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obter(OUTRO_USUARIO, OBJETIVO_ID))
                .isInstanceOf(ObjetivoNaoEncontradoException.class)
                .satisfies(ex -> {
                    ErrorResponseException erro = (ErrorResponseException) ex;
                    assertThat(erro.getStatusCode().value()).isEqualTo(404);
                    assertThat(erro.getBody().getTitle()).isEqualTo("Objetivo não encontrado");
                });
        verify(objetivoVinculoService, never()).resolverConjunto(any());
    }

    @Test
    void deveRetornar404ParaObjetivoInexistente() {
        UUID inexistente = UUID.randomUUID();
        when(objetivoRepository.findByIdAndUsuarioId(inexistente, USUARIO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obter(USUARIO, inexistente))
                .isInstanceOf(ObjetivoNaoEncontradoException.class);
    }

    @Test
    void deveCalcularPeloConjuntoResolvidoDoObjetivoDoDono() {
        when(objetivoRepository.findByIdAndUsuarioId(OBJETIVO_ID, USUARIO)).thenReturn(Optional.of(objetivo()));
        when(objetivoVinculoService.resolverConjunto(OBJETIVO_ID))
                .thenReturn(conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.EM_PROGRESSO, 60))));
        stubLinhas(linha(TAREFA_A, dia(3), 30));

        ObjetivoEvolucaoResponse evolucao = service.obter(USUARIO, OBJETIVO_ID);

        assertThat(evolucao.pontos()).containsExactly(new PontoEvolucaoResponse(dia(3), 30, 30));
    }

    // --- helpers -------------------------------------------------------------------------------

    private ObjetivoEvolucaoService serviceCom(Instant instante) {
        return new ObjetivoEvolucaoService(
                objetivoRepository, objetivoVinculoService, tarefaRepository, execucaoRepository,
                Clock.fixed(instante, ZoneOffset.UTC));
    }

    private ObjetivoProgressoService progressoCom(Instant instante) {
        return new ObjetivoProgressoService(tarefaRepository, execucaoRepository, Clock.fixed(instante, ZoneOffset.UTC));
    }

    private void stubLinhas(MinutoTarefaLinha... linhas) {
        when(execucaoRepository.listarMinutosConcluidosPorTarefas(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(List.of(linhas));
    }

    /** Alimenta evolução e progresso com as mesmas execuções, para comparar os dois. */
    private void stubTudoCoerente(List<MinutoTarefaLinha> linhas, List<Tarefa> filhos) {
        lenient().when(tarefaRepository.findByParentIdInAndDeletedAtIsNull(any())).thenReturn(filhos);
        lenient().when(execucaoRepository.listarMinutosConcluidosPorTarefas(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(linhas);
        Map<UUID, UUID> raizDoFilho = filhos.stream()
                .collect(Collectors.toMap(Tarefa::getId, Tarefa::getParentId));
        Map<UUID, Long> somaPorRaiz = linhas.stream().collect(Collectors.groupingBy(
                linha -> raizDoFilho.getOrDefault(linha.tarefaId(), linha.tarefaId()),
                Collectors.summingLong(MinutoTarefaLinha::minutos)));
        List<Object[]> somas = new ArrayList<>();
        somaPorRaiz.forEach((tarefaId, soma) -> somas.add(new Object[] {tarefaId, soma}));
        lenient().when(execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(somas);
    }

    private void stubCumprimento(LocalDate data, int minutos) {
        when(execucaoRepository.listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any()))
                .thenReturn(List.of(cumprimento(data, minutos)));
    }

    private static CumprimentoHabitoLinha cumprimento(LocalDate data, int minutos) {
        return new CumprimentoHabitoLinha(HABITO_ID, data, minutos, ANTES, ANTES, minutos);
    }

    private static MinutoTarefaLinha linha(UUID tarefaId, LocalDate dia, int minutos) {
        return new MinutoTarefaLinha(tarefaId, instanteBrt(dia), dia, minutos);
    }

    private static LocalDate dia(int diaDeNovembro) {
        return LocalDate.of(2026, 11, diaDeNovembro);
    }

    private static Objetivo objetivo() {
        return new Objetivo(OBJETIVO_ID, USUARIO, "Meta", null, null, ANTES);
    }

    private static ObjetivoConjunto conjunto(ObjetivoConjunto.TarefaRaiz... raizes) {
        return new ObjetivoConjunto(List.of(raizes), List.of(), List.of());
    }

    private static ObjetivoConjunto conjuntoHabito(Habito habito, Instant vinculoCreatedAt) {
        return new ObjetivoConjunto(
                List.of(), List.of(new ObjetivoConjunto.HabitoVinculado(habito, vinculoCreatedAt)), List.of());
    }

    private static ObjetivoConjunto.TarefaRaiz raiz(Tarefa tarefa) {
        return new ObjetivoConjunto.TarefaRaiz(tarefa, OrigemVinculoObjetivo.DIRETO, null);
    }

    private static Tarefa tarefa(UUID id, StatusTarefa status, int duracao) {
        return new Tarefa(id, USUARIO, "T", "desc", Prioridade.MEDIA, null, status, duracao, ANTES, ANTES);
    }

    /** Tarefa finalizada com data registrada (pela própria entidade, como acontece em produção). */
    private static Tarefa finalizada(UUID id, int duracao, Instant finalizadaEm) {
        Tarefa tarefa = tarefa(id, StatusTarefa.PENDENTE, duracao);
        tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, finalizadaEm);
        return tarefa;
    }

    private static Habito habito(StatusHabito status, int duracao, Set<DiaSemana> dias) {
        return new Habito(HABITO_ID, USUARIO, "H", null, duracao, dias, null, status, 0, 0, ANTES, ANTES);
    }

    private static Set<DiaSemana> todosOsDias() {
        return EnumSet.allOf(DiaSemana.class);
    }

    private static Instant instanteBrt(LocalDate dia) {
        return LocalDateTime.of(dia.getYear(), dia.getMonth(), dia.getDayOfMonth(), 12, 0)
                .atZone(PeriodoBRT.ZONE)
                .toInstant();
    }
}
