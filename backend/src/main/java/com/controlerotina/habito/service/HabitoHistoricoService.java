package com.controlerotina.habito.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.CumprimentoHabitoLinha;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.dto.HabitoHistoricoDiaResponse;
import com.controlerotina.habito.dto.OrigemTempo;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;
import com.controlerotina.shared.periodo.IntervaloDatas;

/**
 * Histórico diário de um hábito, calculado na leitura a partir das execuções {@code CONCLUIDO}.
 * Meta do dia = duração do bloco naquele dia (não a duração atual do hábito).
 */
@Service
public class HabitoHistoricoService {

    static final int DIAS_PADRAO = 84;
    static final int INTERVALO_MAXIMO_DIAS = 366;

    private final HabitoRepository habitoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;

    public HabitoHistoricoService(
            HabitoRepository habitoRepository,
            ExecucaoRepository execucaoRepository,
            Clock clock
    ) {
        this.habitoRepository = habitoRepository;
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HabitoHistoricoDiaResponse> listar(
            UUID usuarioId,
            UUID habitoId,
            LocalDate dataDe,
            LocalDate dataAte
    ) {
        if (dataDe == null && dataAte == null) {
            dataAte = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
            dataDe = dataAte.minusDays(DIAS_PADRAO - 1L);
        }
        IntervaloDatas.validar(dataDe, dataAte, INTERVALO_MAXIMO_DIAS);
        habitoRepository.findByIdAndUsuarioId(habitoId, usuarioId)
                .orElseThrow(HabitoNaoEncontradoException::new);

        List<CumprimentoHabitoLinha> linhas = execucaoRepository.listarCumprimentosHabitoNoIntervalo(
                usuarioId, habitoId, dataDe, dataAte, StatusExecucao.CONCLUIDO);
        return agrupar(linhas);
    }

    /** Agrupa por data preservando a ordem decrescente da consulta. */
    static List<HabitoHistoricoDiaResponse> agrupar(List<CumprimentoHabitoLinha> linhas) {
        Map<LocalDate, Acumulador> porDia = new LinkedHashMap<>();
        for (CumprimentoHabitoLinha linha : linhas) {
            porDia.computeIfAbsent(linha.data(), d -> new Acumulador()).somar(linha);
        }
        List<HabitoHistoricoDiaResponse> dias = new ArrayList<>(porDia.size());
        porDia.forEach((data, acc) -> dias.add(acc.paraResposta(data)));
        return dias;
    }

    /** Minutos de uma execução: informados se houver, senão o intervalo bruto do timer (sem a regra de 80%). */
    static int minutosDedicados(CumprimentoHabitoLinha linha) {
        if (linha.minutosInformados() != null) {
            return linha.minutosInformados();
        }
        if (linha.iniciadoEm() == null || linha.concluidoEm() == null) {
            return 0;
        }
        long millis = Duration.between(linha.iniciadoEm(), linha.concluidoEm()).toMillis();
        return millis <= 0 ? 0 : (int) Math.round(millis / 60_000.0);
    }

    private static final class Acumulador {
        private int minutos;
        private int meta;
        private boolean temTimer;
        private boolean temInformado;

        void somar(CumprimentoHabitoLinha linha) {
            minutos += minutosDedicados(linha);
            meta = Math.max(meta, linha.duracaoBlocoMinutos());
            if (linha.minutosInformados() != null) {
                temInformado = true;
            } else {
                temTimer = true;
            }
        }

        HabitoHistoricoDiaResponse paraResposta(LocalDate data) {
            OrigemTempo origem = temTimer && temInformado
                    ? OrigemTempo.MISTO
                    : temInformado ? OrigemTempo.INFORMADO : OrigemTempo.TIMER;
            return new HabitoHistoricoDiaResponse(data, minutos, meta, minutos >= meta, origem);
        }
    }
}
