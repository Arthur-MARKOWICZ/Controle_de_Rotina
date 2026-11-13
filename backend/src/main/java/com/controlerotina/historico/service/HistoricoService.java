package com.controlerotina.historico.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.historico.CalibracaoCalculadora;
import com.controlerotina.historico.HistoricoAgregador;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.historico.dto.CalibracaoResponse;
import com.controlerotina.historico.dto.HistoricoResumoResponse;
import com.controlerotina.historico.dto.MetricasCalibracaoResponse;
import com.controlerotina.historico.dto.PeriodoHistoricoResponse;
import com.controlerotina.historico.dto.RecorteProjetoCalibracaoResponse;
import com.controlerotina.historico.dto.RecorteTagCalibracaoResponse;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.tag.entity.TarefaTag;
import com.controlerotina.tag.repository.TagRepository;
import com.controlerotina.tag.repository.TarefaTagRepository;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.repository.XpEventoRepository;

@Service
public class HistoricoService {

    private final XpEventoRepository xpEventoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final TarefaTagRepository tarefaTagRepository;
    private final TagRepository tagRepository;
    private final ProjetoRepository projetoRepository;
    private final Clock clock;

    public HistoricoService(
            XpEventoRepository xpEventoRepository,
            ExecucaoRepository execucaoRepository,
            TarefaTagRepository tarefaTagRepository,
            TagRepository tagRepository,
            ProjetoRepository projetoRepository,
            Clock clock
    ) {
        this.xpEventoRepository = xpEventoRepository;
        this.execucaoRepository = execucaoRepository;
        this.tarefaTagRepository = tarefaTagRepository;
        this.tagRepository = tagRepository;
        this.projetoRepository = projetoRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HistoricoResumoResponse resumirDia(UUID usuarioId, LocalDate data) {
        LocalDate dia = data != null
                ? data
                : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloDia(dia);
        return montarResumo(usuarioId, "DIA", intervalo);
    }

    @Transactional(readOnly = true)
    public HistoricoResumoResponse resumirSemana(UUID usuarioId, LocalDate semanaInicio) {
        LocalDate inicio = semanaInicio != null
                ? semanaInicio
                : PeriodoBRT.segundaDaSemana(LocalDate.now(clock.withZone(PeriodoBRT.ZONE)));
        PeriodoBRT.Intervalo intervalo = PeriodoBRT.intervaloSemana(inicio);
        return montarResumo(usuarioId, "SEMANA", intervalo);
    }

    /** Razão real ÷ planejado dos últimos 90 dias BRT (issue #71, fase 1 — só leitura). */
    @Transactional(readOnly = true)
    public CalibracaoResponse calibrar(UUID usuarioId) {
        LocalDate hoje = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        PeriodoBRT.Intervalo janela = PeriodoBRT.intervaloUltimosDias(hoje, CalibracaoCalculadora.JANELA_DIAS);

        List<Object[]> linhas = execucaoRepository.listarAmostrasCalibracao(
                usuarioId,
                StatusExecucao.CONCLUIDO,
                janela.inicioInclusivo(),
                janela.fimExclusivo()
        );

        Set<UUID> tarefaIds = new HashSet<>();
        Set<UUID> projetoIds = new HashSet<>();
        for (Object[] l : linhas) {
            tarefaIds.add((UUID) l[3]);
            if (l[4] != null) {
                projetoIds.add((UUID) l[4]);
            }
        }

        Map<UUID, Set<UUID>> tagsPorTarefa = tagsPorTarefa(tarefaIds);
        Set<UUID> tagIds = tagsPorTarefa.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        Map<UUID, CalibracaoCalculadora.TagInfo> tags = tagIds.isEmpty() ? Map.of()
                : tagRepository.findAllById(tagIds).stream()
                        .filter(t -> usuarioId.equals(t.getUsuarioId()))
                        .map(t -> new CalibracaoCalculadora.TagInfo(t.getId(), t.getNome(), t.getCor()))
                        .collect(Collectors.toMap(CalibracaoCalculadora.TagInfo::id, Function.identity()));
        Map<UUID, CalibracaoCalculadora.ProjetoInfo> projetos = projetoIds.isEmpty() ? Map.of()
                : projetoRepository.findAllById(projetoIds).stream()
                        .filter(p -> usuarioId.equals(p.getUsuarioId()))
                        .map(p -> new CalibracaoCalculadora.ProjetoInfo(p.getId(), p.getNome()))
                        .collect(Collectors.toMap(CalibracaoCalculadora.ProjetoInfo::id, Function.identity()));

        List<CalibracaoCalculadora.Amostra> amostras = linhas.stream()
                .map(l -> new CalibracaoCalculadora.Amostra(
                        (Instant) l[0],
                        (Instant) l[1],
                        ((Number) l[2]).intValue(),
                        (UUID) l[4],
                        tagsPorTarefa.getOrDefault((UUID) l[3], Set.of())
                ))
                .toList();

        CalibracaoCalculadora.Resultado r = CalibracaoCalculadora.calcular(amostras, tags, projetos);
        return new CalibracaoResponse(
                new CalibracaoResponse.Janela(janela.inicioLocal(), janela.fimLocal()),
                CalibracaoCalculadora.MINIMO_AMOSTRAS,
                MetricasCalibracaoResponse.of(r.geral()),
                r.porTag().stream().map(RecorteTagCalibracaoResponse::of).toList(),
                r.porProjeto().stream().map(RecorteProjetoCalibracaoResponse::of).toList()
        );
    }

    private Map<UUID, Set<UUID>> tagsPorTarefa(Collection<UUID> tarefaIds) {
        if (tarefaIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<UUID>> mapa = new HashMap<>();
        for (TarefaTag tt : tarefaTagRepository.findByTarefaIdIn(tarefaIds)) {
            mapa.computeIfAbsent(tt.getTarefaId(), k -> new HashSet<>()).add(tt.getTagId());
        }
        return mapa;
    }

    private HistoricoResumoResponse montarResumo(
            UUID usuarioId,
            String escopo,
            PeriodoBRT.Intervalo intervalo
    ) {
        int tarefas = (int) xpEventoRepository.contarPorTipoNoPeriodo(
                usuarioId,
                TipoXpEvento.TAREFA_FINALIZADA,
                intervalo.inicioInclusivo(),
                intervalo.fimExclusivo()
        );
        int xp = (int) xpEventoRepository.somarQuantidadeNoPeriodo(
                usuarioId,
                intervalo.inicioInclusivo(),
                intervalo.fimExclusivo()
        );
        int blocos = (int) execucaoRepository.contarConcluidasNoPeriodo(
                usuarioId,
                StatusExecucao.CONCLUIDO,
                intervalo.inicioInclusivo(),
                intervalo.fimExclusivo()
        );
        int minutos = (int) execucaoRepository.somarDuracaoMinutosConcluidasNoPeriodo(
                usuarioId,
                StatusExecucao.CONCLUIDO,
                intervalo.inicioInclusivo(),
                intervalo.fimExclusivo()
        );

        return HistoricoResumoResponse.of(
                new PeriodoHistoricoResponse(escopo, intervalo.inicioLocal(), intervalo.fimLocal()),
                new HistoricoAgregador.Metricas(tarefas, blocos, minutos, xp)
        );
    }
}
