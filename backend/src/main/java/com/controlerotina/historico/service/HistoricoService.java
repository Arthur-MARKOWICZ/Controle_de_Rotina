package com.controlerotina.historico.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.historico.HistoricoAgregador;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.historico.dto.HistoricoResumoResponse;
import com.controlerotina.historico.dto.PeriodoHistoricoResponse;
import com.controlerotina.xp.entity.TipoXpEvento;
import com.controlerotina.xp.repository.XpEventoRepository;

@Service
public class HistoricoService {

    private final XpEventoRepository xpEventoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public HistoricoService(
            XpEventoRepository xpEventoRepository,
            ExecucaoRepository execucaoRepository,
            Clock clock
    ) {
        this.xpEventoRepository = xpEventoRepository;
        this.execucaoRepository = execucaoRepository;
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
