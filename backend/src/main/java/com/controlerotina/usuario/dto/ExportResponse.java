package com.controlerotina.usuario.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.habito.entity.StatusHabito;
import com.controlerotina.objetivo.entity.StatusObjetivo;
import com.controlerotina.pendencia.entity.TipoRecursoEspera;
import com.controlerotina.planner.dto.ItemNaoAlocadoResponse;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.xp.entity.TipoXpEvento;

/**
 * Todos os dados do titular (LGPD, `GET /me/export`). Formato próprio, desacoplado dos DTOs de tela:
 * inclui registros apagados logicamente e arquivados, e liga os domínios só por id.
 * Nunca contém hash de senha nem refresh tokens.
 */
public record ExportResponse(
        int versao,
        Instant geradoEm,
        Conta conta,
        List<TarefaExport> tarefas,
        List<ProjetoExport> projetos,
        List<ReservaProjetoExport> reservasProjeto,
        List<TagExport> tags,
        List<VinculoTagExport> vinculosTag,
        List<HabitoExport> habitos,
        List<ItemDecidirExport> itensDecidir,
        List<PendenciaExport> pendencias,
        List<ObjetivoExport> objetivos,
        List<VinculoObjetivoExport> vinculosObjetivo,
        List<CapacidadeSemanalExport> capacidadeSemanal,
        List<CapacidadeOverrideExport> capacidadeOverrides,
        List<PlanoExport> planos,
        List<BlocoPlanoExport> blocosPlano,
        List<ExecucaoExport> execucoes,
        List<XpEventoExport> eventosXp
) {

    public static final int VERSAO_ATUAL = 1;

    /** Tipo do item ligado a uma tag ou a um objetivo. */
    public enum TipoItemVinculo { TAREFA, HABITO, PROJETO, DECIDIR }

    public record Conta(
            UUID id,
            String email,
            String nome,
            int xpTotal,
            int nivel,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TarefaExport(
            UUID id,
            String titulo,
            @Nullable String descricao,
            Prioridade prioridade,
            @Nullable Instant prazo,
            StatusTarefa status,
            int duracaoMinutos,
            @Nullable UUID projetoId,
            @Nullable UUID parentId,
            int ordem,
            @Nullable Instant finalizadaEm,
            Instant createdAt,
            Instant updatedAt,
            @Nullable Instant deletedAt
    ) {
    }

    public record ProjetoExport(
            UUID id,
            String nome,
            @Nullable String descricao,
            StatusProjeto status,
            int ordem,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ReservaProjetoExport(
            UUID id,
            UUID projetoId,
            DiaSemana diaSemana,
            int minutosReservados,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TagExport(UUID id, String nome, String cor, Instant createdAt, Instant updatedAt) {
    }

    public record VinculoTagExport(UUID tagId, TipoItemVinculo tipo, UUID itemId) {
    }

    public record HabitoExport(
            UUID id,
            String titulo,
            @Nullable String descricao,
            int duracaoMinutos,
            Set<DiaSemana> diasSemana,
            @Nullable LocalTime horarioPreferencial,
            StatusHabito status,
            int streakAtual,
            int melhorStreak,
            int ordem,
            Instant createdAt,
            Instant updatedAt,
            @Nullable Instant deletedAt
    ) {
    }

    public record ItemDecidirExport(
            UUID id,
            String titulo,
            @Nullable String descricao,
            @Nullable Instant decidirAte,
            @Nullable UUID projetoId,
            @Nullable UUID tarefaId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PendenciaExport(
            UUID id,
            TipoRecursoEspera origemTipo,
            UUID origemId,
            TipoRecursoEspera alvoTipo,
            UUID alvoId,
            Instant createdAt
    ) {
    }

    public record ObjetivoExport(
            UUID id,
            String titulo,
            @Nullable String descricao,
            @Nullable LocalDate prazo,
            StatusObjetivo status,
            @Nullable Instant concluidoEm,
            @Nullable Instant arquivadoEm,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record VinculoObjetivoExport(UUID objetivoId, TipoItemVinculo tipo, UUID itemId) {
    }

    public record CapacidadeSemanalExport(
            UUID id,
            DiaSemana diaSemana,
            int horas,
            boolean ativo,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record CapacidadeOverrideExport(
            UUID id,
            LocalDate data,
            int horas,
            boolean ativo,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PlanoExport(
            UUID id,
            LocalDate data,
            int versao,
            StatusPlano status,
            boolean temConflito,
            @Nullable String mensagem,
            @Nullable UUID propostaId,
            List<ItemNaoAlocadoResponse> itensNaoAlocados,
            Instant geradoEm,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record BlocoPlanoExport(
            UUID id,
            UUID planoId,
            @Nullable UUID tarefaId,
            @Nullable UUID habitoId,
            int duracaoMinutos,
            int ordem,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ExecucaoExport(
            UUID id,
            UUID blocoPlanoId,
            StatusExecucao status,
            @Nullable Instant iniciadoEm,
            @Nullable Instant concluidoEm,
            @Nullable Instant abortadoEm,
            @Nullable Integer minutosInformados,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record XpEventoExport(UUID id, TipoXpEvento tipo, UUID origemId, int quantidade, Instant createdAt) {
    }
}
