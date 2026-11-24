package com.controlerotina.objetivo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.CumprimentoHabitoLinha;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.objetivo.dto.ObjetivoProgressoResponse;
import com.controlerotina.objetivo.dto.OrigemVinculoObjetivo;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@ExtendWith(MockitoExtension.class)
class ObjetivoProgressoServiceTest {

    /** Quarta 18/11/2026 12:00 em BRT. */
    private static final Instant AGORA = Instant.parse("2026-11-18T15:00:00Z");
    private static final Instant ANTES = Instant.parse("2026-11-01T12:00:00Z");
    private static final UUID USUARIO = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OBJETIVO_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TAREFA_A = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TAREFA_B = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID FILHO_A = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID FILHO_B = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID HABITO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID HABITO_B = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID OBJETIVO_B = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID PROJETO_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    TarefaRepository tarefaRepository;
    @Mock
    ExecucaoRepository execucaoRepository;

    private ObjetivoProgressoService service;

    @BeforeEach
    void setUp() {
        service = serviceCom(AGORA);
    }

    @Test
    void deveCalcular80PorCentoComTarefaFinalizadaEPendente() {
        ObjetivoConjunto conjunto = conjunto(
                raiz(tarefa(TAREFA_A, StatusTarefa.FINALIZADA, 120), OrigemVinculoObjetivo.DIRETO),
                raiz(tarefa(TAREFA_B, StatusTarefa.PENDENTE, 30), OrigemVinculoObjetivo.DIRETO)
        );

        ObjetivoProgressoResponse progresso = service.calcular(objetivo(), conjunto);

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(80, 120, 150));
    }

    @Test
    void deveContarParcialNaoFinalizadaComo60De120() {
        when(execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(somas(TAREFA_A, 60L));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.PENDENTE, 120), OrigemVinculoObjetivo.DIRETO))
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(50, 60, 120));
    }

    @Test
    void deveLimitarExecucoesAcimaDaDuracaoDaRaiz() {
        when(execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(somas(TAREFA_A, 45L));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.PENDENTE, 30), OrigemVinculoObjetivo.DIRETO))
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(100, 30, 30));
    }

    @Test
    void deveIgnorarTarefaSoftDeletedNoTotal() {
        Tarefa ativa = tarefa(TAREFA_A, StatusTarefa.PENDENTE, 60);
        Tarefa deletada = tarefa(TAREFA_B, StatusTarefa.PENDENTE, 40);
        deletada.softDelete(AGORA);

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(
                        raiz(ativa, OrigemVinculoObjetivo.DIRETO),
                        raiz(deletada, OrigemVinculoObjetivo.DIRETO)
                )
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 60));

        deletada.restore(AGORA);
        ObjetivoProgressoResponse aposRestore = service.calcular(
                objetivo(),
                conjunto(
                        raiz(ativa, OrigemVinculoObjetivo.DIRETO),
                        raiz(deletada, OrigemVinculoObjetivo.DIRETO)
                )
        );
        assertThat(aposRestore).isEqualTo(new ObjetivoProgressoResponse(0, 0, 100));
    }

    @Test
    void deveSomarTarefaUmaVezQuandoDiretaEHerdada() {
        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.FINALIZADA, 90), OrigemVinculoObjetivo.DIRETO))
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(100, 90, 90));
    }

    @Test
    void deveSomarExecucaoDoFilhoNaRaiz() {
        Tarefa raiz = tarefa(TAREFA_A, StatusTarefa.PENDENTE, 60);
        Tarefa filho = tarefa(FILHO_A, StatusTarefa.PENDENTE, 30);
        filho.definirParentId(TAREFA_A);
        when(tarefaRepository.findByParentIdInAndDeletedAtIsNull(any())).thenReturn(List.of(filho));
        when(execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(somas(FILHO_A, 30L));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(raiz(raiz, OrigemVinculoObjetivo.DIRETO))
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(50, 30, 60));
    }

    @Test
    void deveAtribuirFilhosAosRespectivosPais() {
        Tarefa paiA = tarefa(TAREFA_A, StatusTarefa.PENDENTE, 60);
        Tarefa paiB = tarefa(TAREFA_B, StatusTarefa.PENDENTE, 60);
        Tarefa filhoA = tarefa(FILHO_A, StatusTarefa.PENDENTE, 30);
        filhoA.definirParentId(TAREFA_A);
        Tarefa filhoB = tarefa(FILHO_B, StatusTarefa.PENDENTE, 30);
        filhoB.definirParentId(TAREFA_B);
        when(tarefaRepository.findByParentIdInAndDeletedAtIsNull(any())).thenReturn(List.of(filhoA, filhoB));
        when(execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(somas(FILHO_A, 30L, FILHO_B, 15L));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(
                        raiz(paiA, OrigemVinculoObjetivo.DIRETO),
                        raiz(paiB, OrigemVinculoObjetivo.DIRETO)
                )
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(37, 45, 120));
        verify(tarefaRepository).findByParentIdInAndDeletedAtIsNull(Set.of(TAREFA_A, TAREFA_B));
    }

    @Test
    void deveDevolverNullQuandoSoTarefaCancelada() {
        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.CANCELADA, 60), OrigemVinculoObjetivo.DIRETO))
        );

        assertThat(progresso).isNull();
        verify(execucaoRepository, never()).somarDuracaoMinutosPorTarefasEStatus(any(), any(), any());
    }

    @Test
    void deveDevolverNullQuandoNaoHaItens() {
        assertThat(service.calcular(objetivo(), ObjetivoConjunto.vazio())).isNull();
    }

    @Test
    void deveDevolverNullQuandoProjetoNaoTemTarefasRaiz() {
        Projeto projeto = new Projeto(PROJETO_ID, USUARIO, "Vazio", null, StatusProjeto.ATIVO, ANTES, ANTES);

        assertThat(service.calcular(objetivo(), new ObjetivoConjunto(List.of(), List.of(), List.of(projeto))))
                .isNull();
    }

    @Test
    void deveArredondar10De30Para33() {
        when(execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(somas(TAREFA_A, 10L));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.PENDENTE, 30), OrigemVinculoObjetivo.DIRETO))
        );

        assertThat(progresso.percentual()).isEqualTo(33);
        assertThat(progresso.minutosConcluidos()).isEqualTo(10);
        assertThat(progresso.minutosTotais()).isEqualTo(30);
    }

    @Test
    void deveArredondar149De150Para99() {
        when(execucaoRepository.somarDuracaoMinutosPorTarefasEStatus(any(), eq(USUARIO), eq(StatusExecucao.CONCLUIDO)))
                .thenReturn(somas(TAREFA_A, 149L));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjunto(raiz(tarefa(TAREFA_A, StatusTarefa.PENDENTE, 150), OrigemVinculoObjetivo.DIRETO))
        );

        assertThat(progresso.percentual()).isEqualTo(99);
        assertThat(progresso.minutosConcluidos()).isEqualTo(149);
        assertThat(progresso.minutosTotais()).isEqualTo(150);
    }

    @Test
    void deveCalcular100PorCentoComHabitoDeUmDiaCumprido() {
        Instant segundaMeioDia = Instant.parse("2026-11-16T15:00:00Z");
        ObjetivoProgressoService naSegunda = serviceCom(segundaMeioDia);
        stubCumprimento(HABITO_ID, LocalDate.of(2026, 11, 16), 30);

        ObjetivoProgressoResponse progresso = naSegunda.calcular(
                objetivo(),
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, Set.of(DiaSemana.SEGUNDA)), segundaMeioDia)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(100, 30, 30));
    }

    @Test
    void deveCobrirBuracoComExtraNoMesmoDia() {
        Instant inicio = instanteBrt(LocalDate.of(2026, 11, 16));
        stubCumprimento(HABITO_ID, LocalDate.of(2026, 11, 18), 90);

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), inicio)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(100, 90, 90));
    }

    @Test
    void deveMisturarTarefaFinalizadaComHabitoSemCumprimento() {
        Instant hoje = AGORA;
        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                new ObjetivoConjunto(
                        List.of(raiz(tarefa(TAREFA_A, StatusTarefa.FINALIZADA, 120), OrigemVinculoObjetivo.DIRETO)),
                        List.of(new ObjetivoConjunto.HabitoVinculado(
                                habito(StatusHabito.ATIVO, 30, todosOsDias()), hoje)),
                        List.of()
                )
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(80, 120, 150));
    }

    @Test
    void deveIgnorarHabitoPausadoEManterVinculoNoConjunto() {
        ObjetivoConjunto conjunto = conjuntoHabito(
                habito(StatusHabito.PAUSADO, 30, todosOsDias()), AGORA);

        assertThat(service.calcular(objetivo(), conjunto)).isNull();
        verify(execucaoRepository, never()).listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any());
        assertThat(conjunto.habitos()).hasSize(1);
    }

    @Test
    void deveRecolocarDiasPausadosAoReativar() {
        Instant inicio = instanteBrt(LocalDate.of(2026, 11, 9));
        Habito habito = habito(StatusHabito.ATIVO, 30, Set.of(DiaSemana.SEGUNDA));
        habito.pausar(AGORA);
        ObjetivoConjunto conjunto = conjuntoHabito(habito, inicio);

        assertThat(service.calcular(objetivo(), conjunto)).isNull();

        habito.reativar(AGORA);
        ObjetivoProgressoResponse progresso = service.calcular(objetivo(), conjunto);

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 60));
    }

    @Test
    void deveIncluirHojeNoTotalAMeiaNoiteBrt() {
        Instant inicio = instanteBrt(LocalDate.of(2026, 11, 15));
        Instant meiaNoiteDoQuartoDia = Instant.parse("2026-11-18T03:00:00Z");
        ObjetivoProgressoService aposVirada = serviceCom(meiaNoiteDoQuartoDia);
        stubCumprimento(HABITO_ID, LocalDate.of(2026, 11, 15), 90);

        ObjetivoProgressoResponse progresso = aposVirada.calcular(
                objetivo(),
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), inicio)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(75, 90, 120));
    }

    @Test
    void naoDeveIncluirSegundaAindaDomingoAs2330Brt() {
        Instant vinculoSegundaAnterior = instanteBrt(LocalDate.of(2026, 11, 9));
        ObjetivoProgressoService domingo = serviceCom(Instant.parse("2026-11-16T02:30:00Z"));

        ObjetivoProgressoResponse progresso = domingo.calcular(
                objetivo(),
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, Set.of(DiaSemana.SEGUNDA)), vinculoSegundaAnterior)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 30));
    }

    @Test
    void deveIncluirSegundaA0030Brt() {
        Instant vinculoSegundaAnterior = instanteBrt(LocalDate.of(2026, 11, 9));
        ObjetivoProgressoService segunda = serviceCom(Instant.parse("2026-11-16T03:30:00Z"));

        ObjetivoProgressoResponse progresso = segunda.calcular(
                objetivo(),
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, Set.of(DiaSemana.SEGUNDA)), vinculoSegundaAnterior)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 60));
    }

    @Test
    void deveCongelarJanelaNoDiaDaConclusao() {
        Instant inicio = instanteBrt(LocalDate.of(2026, 11, 15));
        Instant concluidoOntem = instanteBrt(LocalDate.of(2026, 11, 17));
        Objetivo objetivo = objetivo();
        objetivo.concluir(concluidoOntem);

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo,
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), inicio)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 90));
        assertThat(objetivo.getStatus()).isEqualTo(StatusObjetivo.CONCLUIDO);
    }

    @Test
    void deveCongelarJanelaNoDiaDoArquivamento() {
        Instant inicio = instanteBrt(LocalDate.of(2026, 11, 15));
        Objetivo objetivo = objetivo();
        objetivo.arquivar(instanteBrt(LocalDate.of(2026, 11, 17)));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo,
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), inicio)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 90));
        assertThat(objetivo.getStatus()).isEqualTo(StatusObjetivo.ARQUIVADO);
    }

    @Test
    void deveUsarArquivamentoQuandoAnteriorAConclusao() {
        Instant inicio = instanteBrt(LocalDate.of(2026, 11, 15));
        Objetivo objetivo = objetivo();
        objetivo.concluir(instanteBrt(LocalDate.of(2026, 11, 17)));
        objetivo.arquivar(instanteBrt(LocalDate.of(2026, 11, 16)));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo,
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), inicio)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 60));
    }

    @Test
    void deveManterCongelamentoDaConclusaoQuandoArquivadoDepois() {
        Instant inicio = instanteBrt(LocalDate.of(2026, 11, 15));
        Objetivo objetivo = objetivo();
        objetivo.concluir(instanteBrt(LocalDate.of(2026, 11, 16)));
        objetivo.arquivar(instanteBrt(LocalDate.of(2026, 11, 17)));

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo,
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), inicio)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 60));
    }

    @Test
    void naoDeveContarHabitoQuandoVinculoEhPosteriorAoArquivamento() {
        Objetivo objetivo = objetivo();
        objetivo.arquivar(instanteBrt(LocalDate.of(2026, 11, 17)));

        assertThat(service.calcular(
                objetivo,
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), AGORA)
        )).isNull();
        verify(execucaoRepository, never()).listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any());
    }

    @Test
    void deveDevolverMapaVazioQuandoLoteEhNulo() {
        assertThat(service.calcularLote(null, Map.of())).isEmpty();
    }

    @Test
    void deveDevolverMapaVazioQuandoLoteSoTemNulos() {
        List<Objetivo> lote = new ArrayList<>();
        lote.add(null);

        assertThat(service.calcularLote(lote, Map.of())).isEmpty();
    }

    @Test
    void deveConsultarCumprimentosNoEnvelopeDasJanelasDoLote() {
        Objetivo comPrazo = new Objetivo(OBJETIVO_ID, USUARIO, "A", null, LocalDate.of(2026, 11, 16), ANTES);
        Objetivo ativo = new Objetivo(OBJETIVO_B, USUARIO, "B", null, null, ANTES);
        Habito habitoB = new Habito(
                HABITO_B,
                USUARIO,
                "H2",
                null,
                30,
                todosOsDias(),
                null,
                StatusHabito.ATIVO,
                0,
                0,
                ANTES,
                ANTES
        );
        when(execucaoRepository.listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        Map<UUID, ObjetivoProgressoResponse> lote = service.calcularLote(
                List.of(comPrazo, ativo),
                Map.of(
                        OBJETIVO_ID, conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), instanteBrt(LocalDate.of(2026, 11, 15))),
                        OBJETIVO_B, conjuntoHabito(habitoB, instanteBrt(LocalDate.of(2026, 11, 10)))
                )
        );

        assertThat(lote.get(OBJETIVO_ID)).isEqualTo(new ObjetivoProgressoResponse(0, 0, 60));
        assertThat(lote.get(OBJETIVO_B)).isEqualTo(new ObjetivoProgressoResponse(0, 0, 270));
        verify(execucaoRepository).listarCumprimentosHabitosNoIntervalo(
                eq(USUARIO),
                any(),
                eq(LocalDate.of(2026, 11, 10)),
                eq(LocalDate.of(2026, 11, 18)),
                eq(StatusExecucao.CONCLUIDO)
        );
    }

    @Test
    void deveCortarJanelaNoPrazoVencido() {
        Instant inicio = instanteBrt(LocalDate.of(2026, 11, 15));
        Objetivo objetivo = new Objetivo(OBJETIVO_ID, USUARIO, "Prazo", null, LocalDate.of(2026, 11, 17), ANTES);

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo,
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), inicio)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 90));
    }

    @Test
    void naoDeveContarExecucaoAnteriorAoVinculo() {
        stubCumprimento(HABITO_ID, LocalDate.of(2026, 11, 17), 30);

        ObjetivoProgressoResponse progresso = service.calcular(
                objetivo(),
                conjuntoHabito(habito(StatusHabito.ATIVO, 30, todosOsDias()), AGORA)
        );

        assertThat(progresso).isEqualTo(new ObjetivoProgressoResponse(0, 0, 30));
    }

    @Test
    void deveIgnorarHabitoSoftDeleted() {
        Habito habito = habito(StatusHabito.ATIVO, 30, todosOsDias());
        habito.softDelete(AGORA);
        ObjetivoConjunto conjunto = conjuntoHabito(habito, AGORA);

        assertThat(service.calcular(objetivo(), conjunto)).isNull();

        habito.restore(AGORA);
        assertThat(service.calcular(objetivo(), conjunto))
                .isEqualTo(new ObjetivoProgressoResponse(0, 0, 30));
    }

    private ObjetivoProgressoService serviceCom(Instant instante) {
        return new ObjetivoProgressoService(
                tarefaRepository,
                execucaoRepository,
                Clock.fixed(instante, ZoneOffset.UTC)
        );
    }

    private void stubCumprimento(UUID habitoId, LocalDate data, int minutos) {
        when(execucaoRepository.listarCumprimentosHabitosNoIntervalo(any(), any(), any(), any(), any()))
                .thenReturn(List.of(new CumprimentoHabitoLinha(habitoId, data, minutos, ANTES, ANTES, minutos)));
    }

    private static Objetivo objetivo() {
        return new Objetivo(OBJETIVO_ID, USUARIO, "Meta", null, null, ANTES);
    }

    private static ObjetivoConjunto conjunto(ObjetivoConjunto.TarefaRaiz... raizes) {
        return new ObjetivoConjunto(List.of(raizes), List.of(), List.of());
    }

    private static ObjetivoConjunto conjuntoHabito(Habito habito, Instant vinculoCreatedAt) {
        return new ObjetivoConjunto(
                List.of(),
                List.of(new ObjetivoConjunto.HabitoVinculado(habito, vinculoCreatedAt)),
                List.of()
        );
    }

    private static ObjetivoConjunto.TarefaRaiz raiz(Tarefa tarefa, OrigemVinculoObjetivo origem) {
        return new ObjetivoConjunto.TarefaRaiz(tarefa, origem, null);
    }

    private static Tarefa tarefa(UUID id, StatusTarefa status, int duracao) {
        return new Tarefa(
                id,
                USUARIO,
                "T",
                "desc",
                Prioridade.MEDIA,
                null,
                status,
                duracao,
                ANTES,
                ANTES
        );
    }

    private static Habito habito(StatusHabito status, int duracao, Set<DiaSemana> dias) {
        return new Habito(
                HABITO_ID,
                USUARIO,
                "H",
                null,
                duracao,
                dias,
                null,
                status,
                0,
                0,
                ANTES,
                ANTES
        );
    }

    private static Set<DiaSemana> todosOsDias() {
        return EnumSet.allOf(DiaSemana.class);
    }

    private static List<Object[]> somas(Object... pares) {
        List<Object[]> linhas = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            linhas.add(new Object[] {pares[i], pares[i + 1]});
        }
        return linhas;
    }

    private static Instant instanteBrt(LocalDate dia) {
        return LocalDateTime.of(dia.getYear(), dia.getMonth(), dia.getDayOfMonth(), 12, 0)
                .atZone(PeriodoBRT.ZONE)
                .toInstant();
    }
}
