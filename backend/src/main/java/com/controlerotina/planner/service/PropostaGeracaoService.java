package com.controlerotina.planner.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.motor.MotorAlocacao;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;

/**
 * Detecta gatilhos e gera proposta (rascunhos) sem mutar planos {@code APROVADO}.
 */
@Service
public class PropostaGeracaoService {

    private static final Logger log = LoggerFactory.getLogger(PropostaGeracaoService.class);

    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final PlannerGeracaoService plannerGeracaoService;
    private final Clock clock;

    public PropostaGeracaoService(
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            ExecucaoRepository execucaoRepository,
            PlannerGeracaoService plannerGeracaoService,
            Clock clock
    ) {
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.execucaoRepository = execucaoRepository;
        this.plannerGeracaoService = plannerGeracaoService;
        this.clock = clock;
    }

    public void gerarSeDiaAprovado(UUID usuarioId, LocalDate data) {
        if (!planoRepository.existsByUsuarioIdAndDataAndStatus(usuarioId, data, StatusPlano.APROVADO)) {
            return;
        }
        tentarGerarProposta(usuarioId, data);
    }

    public void gerarSeDiaSemanaAprovado(UUID usuarioId, DiaSemana diaSemana) {
        LocalDate hoje = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        for (int i = 0; i < 14; i++) {
            LocalDate dia = hoje.plusDays(i);
            if (DiaSemana.de(dia) == diaSemana
                    && planoRepository.existsByUsuarioIdAndDataAndStatus(
                            usuarioId, dia, StatusPlano.APROVADO)) {
                tentarGerarProposta(usuarioId, dia);
                return;
            }
        }
    }

    public void gerarSeTarefaNaJanelaAprovada(UUID usuarioId, Instant prazo) {
        LocalDate hoje = LocalDate.now(clock.withZone(PeriodoBRT.ZONE));
        LocalDate fimBusca = prazo == null
                ? hoje.plusDays(MotorAlocacao.TETO_JANELA_DIAS - 1L)
                : prazo.atZone(PeriodoBRT.ZONE).toLocalDate();
        if (fimBusca.isBefore(hoje)) {
            return;
        }
        for (LocalDate dia = hoje; !dia.isAfter(fimBusca); dia = dia.plusDays(1)) {
            if (planoRepository.existsByUsuarioIdAndDataAndStatus(usuarioId, dia, StatusPlano.APROVADO)) {
                tentarGerarProposta(usuarioId, hoje);
                return;
            }
        }
    }

    public boolean gerarSeDiaAnteriorIncompleto(UUID usuarioId, LocalDate dataCorrente) {
        LocalDate anterior = dataCorrente.minusDays(1);
        if (!diaComBlocoNaoConcluido(usuarioId, anterior)) {
            return false;
        }
        tentarGerarPropostaIsolado(usuarioId, dataCorrente);
        return true;
    }

    public void tentarGerarProposta(UUID usuarioId, LocalDate inicio) {
        plannerGeracaoService.gerarProposta(usuarioId, inicio);
    }

    public void tentarGerarPropostaIsolado(UUID usuarioId, LocalDate inicio) {
        try {
            plannerGeracaoService.gerarProposta(usuarioId, inicio);
        } catch (Exception ex) {
            log.error("Falha ao gerar proposta de replanejamento para usuario {} a partir de {}",
                    usuarioId, inicio, ex);
        }
    }

    private boolean diaComBlocoNaoConcluido(UUID usuarioId, LocalDate data) {
        Optional<Plano> aprovado = planoRepository.findFirstByUsuarioIdAndDataAndStatusOrderByVersaoDesc(
                usuarioId, data, StatusPlano.APROVADO);
        if (aprovado.isEmpty()) {
            return false;
        }
        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(aprovado.get().getId());
        if (blocos.isEmpty()) {
            return false;
        }
        for (BlocoPlano bloco : blocos) {
            Optional<Execucao> execucao = execucaoRepository.findByBlocoPlanoId(bloco.getId());
            if (execucao.isEmpty() || execucao.get().getStatus() != StatusExecucao.CONCLUIDO) {
                return true;
            }
        }
        return false;
    }
}
