package com.controlerotina.usuario.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.capacidade.entity.CapacidadeOverride;
import com.controlerotina.capacidade.entity.CapacidadeSemanal;
import com.controlerotina.decidir.entity.ItemDecidir;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.objetivo.entity.Objetivo;
import com.controlerotina.objetivo.entity.ObjetivoHabito;
import com.controlerotina.objetivo.entity.ObjetivoProjeto;
import com.controlerotina.objetivo.entity.ObjetivoTarefa;
import com.controlerotina.pendencia.entity.Espera;
import com.controlerotina.planner.dto.ItemNaoAlocadoResponse;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.ReservaProjetoSemanal;
import com.controlerotina.shared.exception.CredenciaisInvalidasException;
import com.controlerotina.tag.entity.DecidirTag;
import com.controlerotina.tag.entity.HabitoTag;
import com.controlerotina.tag.entity.ProjetoTag;
import com.controlerotina.tag.entity.Tag;
import com.controlerotina.tag.entity.TarefaTag;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.usuario.dto.ExportResponse;
import com.controlerotina.usuario.dto.ExportResponse.TipoItemVinculo;
import com.controlerotina.usuario.entity.Usuario;
import com.controlerotina.usuario.repository.UsuarioRepository;
import com.controlerotina.xp.entity.XpEvento;

import jakarta.persistence.EntityManager;

/**
 * Monta o export LGPD do titular: todas as linhas do usuário em todos os domínios, inclusive
 * apagadas logicamente e arquivadas. Uma consulta por tabela, sem filtro de status.
 */
@Service
public class ExportService {

    private final EntityManager em;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public ExportService(EntityManager em, UsuarioRepository usuarioRepository, Clock clock) {
        this.em = em;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    /** Data (BRT) usada no nome do arquivo baixado. */
    public LocalDate dataDoArquivo() {
        return LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
    }

    @Transactional(readOnly = true)
    public ExportResponse exportar(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(CredenciaisInvalidasException::new);

        List<Tarefa> tarefas = doUsuario(Tarefa.class, usuarioId);
        List<Projeto> projetos = doUsuario(Projeto.class, usuarioId);
        List<Tag> tags = doUsuario(Tag.class, usuarioId);
        List<Habito> habitos = doUsuario(Habito.class, usuarioId);
        List<ItemDecidir> itensDecidir = doUsuario(ItemDecidir.class, usuarioId);
        List<Objetivo> objetivos = doUsuario(Objetivo.class, usuarioId);
        List<Plano> planos = doUsuario(Plano.class, usuarioId);

        return new ExportResponse(
                ExportResponse.VERSAO_ATUAL,
                clock.instant(),
                new ExportResponse.Conta(usuario.getId(), usuario.getEmail(), usuario.getNome(),
                        usuario.getXpTotal(), usuario.getNivel(), usuario.getCreatedAt(), usuario.getUpdatedAt()),
                tarefas.stream().map(t -> new ExportResponse.TarefaExport(t.getId(), t.getTitulo(),
                        t.getDescricao(), t.getPrioridade(), t.getPrazo(), t.getStatus(), t.getDuracaoMinutos(),
                        t.getProjetoId(), t.getParentId(), t.getOrdem(), t.getFinalizadaEm(), t.getCreatedAt(),
                        t.getUpdatedAt(), t.getDeletedAt())).toList(),
                projetos.stream().map(p -> new ExportResponse.ProjetoExport(p.getId(), p.getNome(),
                        p.getDescricao(), p.getStatus(), p.getOrdem(), p.getCreatedAt(), p.getUpdatedAt())).toList(),
                doUsuario(ReservaProjetoSemanal.class, usuarioId).stream()
                        .map(r -> new ExportResponse.ReservaProjetoExport(r.getId(), r.getProjetoId(),
                                r.getDiaSemana(), r.getMinutosReservados(), r.getCreatedAt(), r.getUpdatedAt()))
                        .toList(),
                tags.stream().map(t -> new ExportResponse.TagExport(t.getId(), t.getNome(), t.getCor(),
                        t.getCreatedAt(), t.getUpdatedAt())).toList(),
                vinculosTag(ids(tags, Tag::getId)),
                habitos.stream().map(h -> new ExportResponse.HabitoExport(h.getId(), h.getTitulo(),
                        h.getDescricao(), h.getDuracaoMinutos(), h.getDiasSemana(), h.getHorarioPreferencial(),
                        h.getStatus(), h.getStreakAtual(), h.getMelhorStreak(), h.getOrdem(), h.getCreatedAt(),
                        h.getUpdatedAt(), h.getDeletedAt())).toList(),
                itensDecidir.stream().map(i -> new ExportResponse.ItemDecidirExport(i.getId(), i.getTitulo(),
                        i.getDescricao(), i.getDecidirAte(), i.getProjetoId(), i.getTarefaId(), i.getCreatedAt(),
                        i.getUpdatedAt())).toList(),
                doUsuario(Espera.class, usuarioId).stream()
                        .map(e -> new ExportResponse.PendenciaExport(e.getId(), e.getOrigemTipo(), e.getOrigemId(),
                                e.getAlvoTipo(), e.getAlvoId(), e.getCreatedAt()))
                        .toList(),
                objetivos.stream().map(o -> new ExportResponse.ObjetivoExport(o.getId(), o.getTitulo(),
                        o.getDescricao(), o.getPrazo(), o.getStatus(), o.getConcluidoEm(), o.getArquivadoEm(),
                        o.getCreatedAt(), o.getUpdatedAt())).toList(),
                vinculosObjetivo(ids(objetivos, Objetivo::getId)),
                doUsuario(CapacidadeSemanal.class, usuarioId).stream()
                        .map(c -> new ExportResponse.CapacidadeSemanalExport(c.getId(), c.getDiaSemana(),
                                c.getHoras(), c.isAtivo(), c.getCreatedAt(), c.getUpdatedAt()))
                        .toList(),
                doUsuario(CapacidadeOverride.class, usuarioId).stream()
                        .map(c -> new ExportResponse.CapacidadeOverrideExport(c.getId(), c.getData(), c.getHoras(),
                                c.isAtivo(), c.getCreatedAt(), c.getUpdatedAt()))
                        .toList(),
                planos.stream().map(p -> new ExportResponse.PlanoExport(p.getId(), p.getData(), p.getVersao(),
                        p.getStatus(), p.isTemConflito(), p.getMensagem(), p.getPropostaId(),
                        p.getItensNaoAlocados() == null ? List.of()
                                : p.getItensNaoAlocados().stream().map(ItemNaoAlocadoResponse::from).toList(),
                        p.getGeradoEm(), p.getCreatedAt(), p.getUpdatedAt())).toList(),
                porIds(BlocoPlano.class, "planoId", ids(planos, Plano::getId)).stream()
                        .map(b -> new ExportResponse.BlocoPlanoExport(b.getId(), b.getPlanoId(), b.getTarefaId(),
                                b.getHabitoId(), b.getDuracaoMinutos(), b.getOrdem(), b.getCreatedAt(),
                                b.getUpdatedAt()))
                        .toList(),
                doUsuario(Execucao.class, usuarioId).stream()
                        .map(e -> new ExportResponse.ExecucaoExport(e.getId(), e.getBlocoPlanoId(), e.getStatus(),
                                e.getIniciadoEm(), e.getConcluidoEm(), e.getAbortadoEm(), e.getMinutosInformados(),
                                e.getCreatedAt(), e.getUpdatedAt()))
                        .toList(),
                doUsuario(XpEvento.class, usuarioId).stream()
                        .map(x -> new ExportResponse.XpEventoExport(x.getId(), x.getTipo(), x.getOrigemId(),
                                x.getQuantidade(), x.getCreatedAt()))
                        .toList()
        );
    }

    private List<ExportResponse.VinculoTagExport> vinculosTag(List<UUID> tagIds) {
        List<ExportResponse.VinculoTagExport> vinculos = new ArrayList<>();
        porIds(TarefaTag.class, "tagId", tagIds).forEach(v ->
                vinculos.add(new ExportResponse.VinculoTagExport(v.getTagId(), TipoItemVinculo.TAREFA, v.getTarefaId())));
        porIds(HabitoTag.class, "tagId", tagIds).forEach(v ->
                vinculos.add(new ExportResponse.VinculoTagExport(v.getTagId(), TipoItemVinculo.HABITO, v.getHabitoId())));
        porIds(ProjetoTag.class, "tagId", tagIds).forEach(v ->
                vinculos.add(new ExportResponse.VinculoTagExport(v.getTagId(), TipoItemVinculo.PROJETO, v.getProjetoId())));
        porIds(DecidirTag.class, "tagId", tagIds).forEach(v ->
                vinculos.add(new ExportResponse.VinculoTagExport(v.getTagId(), TipoItemVinculo.DECIDIR,
                        v.getItemDecidirId())));
        return vinculos;
    }

    private List<ExportResponse.VinculoObjetivoExport> vinculosObjetivo(List<UUID> objetivoIds) {
        List<ExportResponse.VinculoObjetivoExport> vinculos = new ArrayList<>();
        porIds(ObjetivoTarefa.class, "objetivoId", objetivoIds).forEach(v -> vinculos.add(
                new ExportResponse.VinculoObjetivoExport(v.getObjetivoId(), TipoItemVinculo.TAREFA, v.getTarefaId())));
        porIds(ObjetivoHabito.class, "objetivoId", objetivoIds).forEach(v -> vinculos.add(
                new ExportResponse.VinculoObjetivoExport(v.getObjetivoId(), TipoItemVinculo.HABITO, v.getHabitoId())));
        porIds(ObjetivoProjeto.class, "objetivoId", objetivoIds).forEach(v -> vinculos.add(
                new ExportResponse.VinculoObjetivoExport(v.getObjetivoId(), TipoItemVinculo.PROJETO,
                        v.getProjetoId())));
        return vinculos;
    }

    private <T> List<T> doUsuario(Class<T> entidade, UUID usuarioId) {
        return em.createQuery(
                        "select e from " + entidade.getSimpleName() + " e where e.usuarioId = :u order by e.createdAt",
                        entidade)
                .setParameter("u", usuarioId)
                .getResultList();
    }

    private <T> List<T> porIds(Class<T> entidade, String campo, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                        "select e from " + entidade.getSimpleName() + " e where e." + campo + " in :ids", entidade)
                .setParameter("ids", ids)
                .getResultList();
    }

    private static <T> List<UUID> ids(List<T> itens, java.util.function.Function<T, UUID> id) {
        return itens.stream().map(id).toList();
    }
}
