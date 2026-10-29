package com.controlerotina.planner.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.controlerotina.capacidade.dto.CapacidadeResolvidaResponse;
import com.controlerotina.capacidade.service.CapacidadeResolvidaService;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.motor.BlocoAlocado;
import com.controlerotina.planner.motor.MotorAlocacao;
import com.controlerotina.planner.motor.ResultadoAlocacao;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.PlanoJaAprovadoException;
import com.controlerotina.shared.exception.PlanoVersaoConflitoException;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class PlannerGeracaoService {

    private static final List<StatusTarefa> STATUS_ELEGIVEIS = List.of(
            StatusTarefa.PENDENTE,
            StatusTarefa.EM_PROGRESSO,
            StatusTarefa.ATRASADA
    );

    private final CapacidadeResolvidaService capacidadeResolvidaService;
    private final TarefaRepository tarefaRepository;
    private final PlanoRepository planoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final ExecucaoRepository execucaoRepository;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public PlannerGeracaoService(
            CapacidadeResolvidaService capacidadeResolvidaService,
            TarefaRepository tarefaRepository,
            PlanoRepository planoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            ExecucaoRepository execucaoRepository,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.capacidadeResolvidaService = capacidadeResolvidaService;
        this.tarefaRepository = tarefaRepository;
        this.planoRepository = planoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.execucaoRepository = execucaoRepository;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Gera a janela multi-dia a partir de {@code inicio} e devolve o plano do dia {@code inicio}.
     * Estratégia sob corrida em {@code uk_plano_usuario_data_versao}:
     * retry controlado <strong>uma</strong> vez; se ainda conflitar → 409.
     */
    public Plano gerar(UUID usuarioId, LocalDate inicio) {
        return gerarComRetry(usuarioId, inicio, false);
    }

    /**
     * Gera rascunhos de proposta mesmo quando já há plano {@code APROVADO} nos dias da janela.
     * Não altera planos aprovados existentes.
     */
    public Plano gerarProposta(UUID usuarioId, LocalDate inicio) {
        return gerarComRetry(usuarioId, inicio, true);
    }

    private Plano gerarComRetry(UUID usuarioId, LocalDate inicio, boolean comoProposta) {
        try {
            return exigirPlano(transactionTemplate.execute(
                    status -> gerarJanelaNaTransacao(usuarioId, inicio, comoProposta)));
        } catch (DataIntegrityViolationException ex) {
            if (!violaUniqueVersao(ex)) {
                throw ex;
            }
            try {
                return exigirPlano(transactionTemplate.execute(
                        status -> gerarJanelaNaTransacao(usuarioId, inicio, comoProposta)));
            } catch (DataIntegrityViolationException retryEx) {
                if (violaUniqueVersao(retryEx)) {
                    throw new PlanoVersaoConflitoException();
                }
                throw retryEx;
            }
        }
    }

    private static Plano exigirPlano(Plano plano) {
        if (plano == null) {
            throw new IllegalStateException("Geração de plano não retornou resultado.");
        }
        return plano;
    }

    private Plano gerarJanelaNaTransacao(UUID usuarioId, LocalDate inicio, boolean comoProposta) {
        boolean inicioAprovado = planoRepository.existsByUsuarioIdAndDataAndStatus(
                usuarioId, inicio, StatusPlano.APROVADO);
        if (inicioAprovado && !comoProposta) {
            throw new PlanoJaAprovadoException(inicio);
        }

        List<Tarefa> candidatas = tarefaRepository.findByUsuarioIdAndDeletedAtIsNullAndStatusIn(
                usuarioId,
                STATUS_ELEGIVEIS
        );
        Map<UUID, Integer> saldoPorTarefa = new HashMap<>();
        List<Tarefa> elegiveis = new ArrayList<>();
        for (Tarefa tarefa : candidatas) {
            long minutosConcluidos = execucaoRepository.somarDuracaoMinutosPorTarefaEStatus(
                    tarefa.getId(),
                    usuarioId,
                    StatusExecucao.CONCLUIDO
            );
            int saldo = MotorAlocacao.calcularSaldoMinutos(tarefa.getDuracaoMinutos(), minutosConcluidos);
            if (saldo > 0) {
                elegiveis.add(tarefa);
                saldoPorTarefa.put(tarefa.getId(), saldo);
            }
        }

        LocalDate fim = MotorAlocacao.fimJanela(inicio, elegiveis);
        List<CapacidadeResolvidaResponse> capacidades = capacidadeResolvidaService
                .resolver(usuarioId, inicio, fim);

        List<MotorAlocacao.DiaCapacidade> dias = capacidades.stream()
                .map(c -> new MotorAlocacao.DiaCapacidade(
                        c.data(),
                        MotorAlocacao.capacidadeMinutos(c.horas())
                ))
                .toList();

        Map<LocalDate, ResultadoAlocacao> alocacaoPorDia = MotorAlocacao.alocarJanela(
                elegiveis,
                saldoPorTarefa,
                dias
        );

        Instant agora = clock.instant();
        UUID propostaId = UUID.randomUUID();
        Plano planoInicio = null;

        for (MotorAlocacao.DiaCapacidade dia : dias) {
            boolean diaAprovado = planoRepository.existsByUsuarioIdAndDataAndStatus(
                    usuarioId, dia.data(), StatusPlano.APROVADO);
            if (diaAprovado && !comoProposta) {
                continue;
            }

            ResultadoAlocacao alocacao = alocacaoPorDia.getOrDefault(
                    dia.data(),
                    new ResultadoAlocacao(List.of(), false, MotorAlocacao.MENSAGEM_SEM_TEMPO)
            );

            int proximaVersao = planoRepository.findMaxVersaoByUsuarioIdAndData(usuarioId, dia.data()) + 1;
            Plano plano = planoRepository.saveAndFlush(new Plano(
                    UUID.randomUUID(),
                    usuarioId,
                    dia.data(),
                    proximaVersao,
                    StatusPlano.RASCUNHO,
                    alocacao.temConflito(),
                    alocacao.mensagem(),
                    propostaId,
                    agora,
                    agora,
                    agora
            ));

            for (BlocoAlocado bloco : alocacao.blocos()) {
                blocoPlanoRepository.save(new BlocoPlano(
                        UUID.randomUUID(),
                        plano.getId(),
                        bloco.tarefaId(),
                        bloco.duracaoMinutos(),
                        bloco.ordem(),
                        agora,
                        agora
                ));
            }

            if (dia.data().equals(inicio)) {
                planoInicio = plano;
            }
        }

        if (planoInicio == null) {
            throw new PlanoJaAprovadoException(inicio);
        }
        return planoInicio;
    }

    private static boolean violaUniqueVersao(DataIntegrityViolationException ex) {
        String mensagem = mensagemCompleta(ex).toLowerCase();
        return mensagem.contains("uk_plano_usuario_data_versao");
    }

    private static String mensagemCompleta(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable atual = ex;
        while (atual != null) {
            if (atual.getMessage() != null) {
                sb.append(' ').append(atual.getMessage());
            }
            atual = atual.getCause();
        }
        return sb.toString();
    }
}
