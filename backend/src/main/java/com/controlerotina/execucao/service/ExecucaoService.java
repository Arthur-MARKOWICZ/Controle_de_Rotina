package com.controlerotina.execucao.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.execucao.dto.BlocoExecucaoResponse;
import com.controlerotina.execucao.dto.ExecucaoResponse;
import com.controlerotina.execucao.dto.ExecucoesDoDiaResponse;
import com.controlerotina.execucao.dto.ItemExecucaoResponse;
import com.controlerotina.execucao.dto.PlanoAprovadoResumoResponse;
import com.controlerotina.execucao.dto.ResultadoConclusao;
import com.controlerotina.execucao.dto.ResultadoInicio;
import com.controlerotina.execucao.entity.Execucao;
import com.controlerotina.execucao.entity.StatusExecucao;
import com.controlerotina.execucao.repository.ExecucaoRepository;
import com.controlerotina.habito.entity.Habito;
import com.controlerotina.habito.repository.HabitoRepository;
import com.controlerotina.habito.service.HabitoStreakService;
import com.controlerotina.historico.PeriodoBRT;
import com.controlerotina.planner.dto.HabitoResumoResponse;
import com.controlerotina.planner.dto.TarefaResumoResponse;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.BlocoPlanoNaoEncontradoException;
import com.controlerotina.shared.exception.ExecucaoBlocoConflitoException;
import com.controlerotina.shared.exception.ExecucaoJaConcluidaException;
import com.controlerotina.shared.exception.ExecucaoJaEmAndamentoException;
import com.controlerotina.shared.exception.ExecucaoNaoEmAndamentoException;
import com.controlerotina.shared.exception.ExecucaoNaoEncontradaException;
import com.controlerotina.shared.exception.HabitoNaoEncontradoException;
import com.controlerotina.shared.exception.PlanoNaoExecutavelException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.xp.XpMotor;
import com.controlerotina.xp.service.XpConcessaoService;

@Service
public class ExecucaoService {

    private static final List<StatusPlano> STATUS_CORRENTE = List.of(
            StatusPlano.RASCUNHO,
            StatusPlano.APROVADO
    );

    private static final Set<StatusTarefa> STATUS_PARA_EM_PROGRESSO = Set.of(
            StatusTarefa.PENDENTE,
            StatusTarefa.ATRASADA
    );

    private final ExecucaoRepository execucaoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final PlanoRepository planoRepository;
    private final TarefaRepository tarefaRepository;
    private final HabitoRepository habitoRepository;
    private final ProjetoRepository projetoRepository;
    private final XpConcessaoService xpConcessaoService;
    private final HabitoStreakService habitoStreakService;
    private final Clock clock;

    public ExecucaoService(
            ExecucaoRepository execucaoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            PlanoRepository planoRepository,
            TarefaRepository tarefaRepository,
            HabitoRepository habitoRepository,
            ProjetoRepository projetoRepository,
            XpConcessaoService xpConcessaoService,
            HabitoStreakService habitoStreakService,
            Clock clock
    ) {
        this.execucaoRepository = execucaoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.planoRepository = planoRepository;
        this.tarefaRepository = tarefaRepository;
        this.habitoRepository = habitoRepository;
        this.projetoRepository = projetoRepository;
        this.xpConcessaoService = xpConcessaoService;
        this.habitoStreakService = habitoStreakService;
        this.clock = clock;
    }

    @Transactional
    public ResultadoInicio iniciar(UUID usuarioId, UUID blocoId) {
        BlocoPlano bloco = carregarBlocoDoUsuario(usuarioId, blocoId);
        garantirPlanoExecutavel(bloco, usuarioId);

        Instant agora = clock.instant();
        var existente = execucaoRepository.findByBlocoPlanoId(bloco.getId());

        if (existente.isEmpty()) {
            try {
                Execucao criada = execucaoRepository.saveAndFlush(Execucao.iniciarNova(
                        UUID.randomUUID(),
                        usuarioId,
                        bloco.getId(),
                        agora
                ));
                marcarTarefaEmProgressoSeAplicavel(bloco, usuarioId, agora);
                return new ResultadoInicio(criada, true);
            } catch (DataIntegrityViolationException ex) {
                if (violaUniqueExecucaoBloco(ex)) {
                    throw new ExecucaoBlocoConflitoException();
                }
                throw ex;
            }
        }

        Execucao execucao = existente.get();
        return switch (execucao.getStatus()) {
            case EM_ANDAMENTO -> throw new ExecucaoJaEmAndamentoException();
            case CONCLUIDO -> throw new ExecucaoJaConcluidaException();
            case ABORTADO, NAO_INICIADO -> {
                execucao.reiniciar(agora);
                Execucao salva = execucaoRepository.save(execucao);
                marcarTarefaEmProgressoSeAplicavel(bloco, usuarioId, agora);
                yield new ResultadoInicio(salva, false);
            }
        };
    }

    private static boolean violaUniqueExecucaoBloco(DataIntegrityViolationException ex) {
        String mensagem = mensagemCompleta(ex).toLowerCase();
        return mensagem.contains("uk_execucao_bloco_plano");
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

    @Transactional
    public Execucao abortar(UUID usuarioId, UUID execucaoId) {
        Execucao execucao = carregarExecucaoDoUsuario(usuarioId, execucaoId);
        BlocoPlano bloco = carregarBlocoDoUsuario(usuarioId, execucao.getBlocoPlanoId());
        garantirPlanoExecutavel(bloco, usuarioId);

        if (execucao.getStatus() != StatusExecucao.EM_ANDAMENTO) {
            throw new ExecucaoNaoEmAndamentoException();
        }

        Instant agora = clock.instant();
        execucao.abortar(agora);
        return execucaoRepository.save(execucao);
    }

    @Transactional
    public ResultadoConclusao concluir(UUID usuarioId, UUID execucaoId) {
        Execucao execucao = carregarExecucaoDoUsuario(usuarioId, execucaoId);
        BlocoPlano bloco = carregarBlocoDoUsuario(usuarioId, execucao.getBlocoPlanoId());
        Plano plano = garantirPlanoExecutavel(bloco, usuarioId);

        if (execucao.getStatus() != StatusExecucao.EM_ANDAMENTO) {
            throw new ExecucaoNaoEmAndamentoException();
        }

        Instant agora = clock.instant();
        int minutosEfetivos = XpMotor.minutosEfetivos(
                bloco.getDuracaoMinutos(),
                execucao.getIniciadoEm(),
                agora
        );

        if (bloco.getHabitoId() != null) {
            return concluirBlocoHabito(usuarioId, bloco, plano, execucao, minutosEfetivos, agora);
        }

        execucao.concluir(agora);
        execucaoRepository.save(execucao);

        Tarefa tarefa = atualizarTarefaAposConcluir(bloco.getTarefaId(), usuarioId, agora);
        int somaEfetivosTarefa = somarMinutosEfetivosConcluidos(bloco.getTarefaId(), usuarioId);
        XpConcessaoService.ResultadoConcessao xp = xpConcessaoService.concederAoConcluir(
                usuarioId,
                execucao.getId(),
                minutosEfetivos,
                somaEfetivosTarefa,
                tarefa
        );
        return new ResultadoConclusao(execucao, xp);
    }

    /**
     * Conclui execução de hábito já materializada (avulso ou plano), inclusive direto
     * sem passar por EM_ANDAMENTO.
     */
    @Transactional
    public ResultadoConclusao concluirHabitoDiretoNoBloco(
            UUID usuarioId,
            BlocoPlano bloco,
            Plano plano,
            Habito habito
    ) {
        if (bloco.getHabitoId() == null || !bloco.getHabitoId().equals(habito.getId())) {
            throw new PlanoNaoExecutavelException();
        }
        Instant agora = clock.instant();
        var existente = execucaoRepository.findByBlocoPlanoId(bloco.getId());

        if (existente.isPresent()) {
            Execucao execucao = existente.get();
            return switch (execucao.getStatus()) {
                case CONCLUIDO -> throw new ExecucaoJaConcluidaException();
                case EM_ANDAMENTO -> {
                    int minutosEfetivos = XpMotor.minutosEfetivos(
                            bloco.getDuracaoMinutos(),
                            execucao.getIniciadoEm(),
                            agora
                    );
                    yield concluirBlocoHabito(usuarioId, bloco, plano, execucao, minutosEfetivos, agora);
                }
                case ABORTADO, NAO_INICIADO -> {
                    boolean jaCumprido = execucaoRepository.existsCumprimentoHabitoNaData(
                            habito.getId(), plano.getData(), StatusExecucao.CONCLUIDO);
                    execucao.concluirDireto(agora);
                    Execucao salva = execucaoRepository.save(execucao);
                    yield finalizarHabitoAposConclusao(
                            usuarioId, habito, plano.getData(), salva, bloco.getDuracaoMinutos(), jaCumprido);
                }
            };
        }

        boolean jaCumprido = execucaoRepository.existsCumprimentoHabitoNaData(
                habito.getId(), plano.getData(), StatusExecucao.CONCLUIDO);
        try {
            Execucao criada = execucaoRepository.saveAndFlush(Execucao.concluirDiretoNova(
                    UUID.randomUUID(),
                    usuarioId,
                    bloco.getId(),
                    agora
            ));
            return finalizarHabitoAposConclusao(
                    usuarioId, habito, plano.getData(), criada, bloco.getDuracaoMinutos(), jaCumprido);
        } catch (DataIntegrityViolationException ex) {
            if (violaUniqueExecucaoBloco(ex)) {
                throw new ExecucaoBlocoConflitoException();
            }
            throw ex;
        }
    }

    private ResultadoConclusao concluirBlocoHabito(
            UUID usuarioId,
            BlocoPlano bloco,
            Plano plano,
            Execucao execucao,
            int minutosEfetivos,
            Instant agora
    ) {
        boolean jaCumprido = execucaoRepository.existsCumprimentoHabitoNaData(
                bloco.getHabitoId(), plano.getData(), StatusExecucao.CONCLUIDO);
        execucao.concluir(agora);
        execucaoRepository.save(execucao);

        Habito habito = habitoRepository.findByIdAndUsuarioId(bloco.getHabitoId(), usuarioId)
                .orElseThrow(HabitoNaoEncontradoException::new);
        habitoStreakService.registrarCumprimento(habito, plano.getData(), jaCumprido);

        XpConcessaoService.ResultadoConcessao xp = xpConcessaoService.concederBlocoHabito(
                usuarioId,
                execucao.getId(),
                minutosEfetivos
        );
        return new ResultadoConclusao(execucao, xp);
    }

    private ResultadoConclusao finalizarHabitoAposConclusao(
            UUID usuarioId,
            Habito habito,
            LocalDate dataPlano,
            Execucao execucao,
            int duracaoMinutos,
            boolean jaCumpridoAntes
    ) {
        habitoStreakService.registrarCumprimento(habito, dataPlano, jaCumpridoAntes);

        int minutosEfetivos = XpMotor.minutosEfetivos(
                duracaoMinutos,
                execucao.getIniciadoEm(),
                execucao.getConcluidoEm()
        );
        XpConcessaoService.ResultadoConcessao xp = xpConcessaoService.concederBlocoHabito(
                usuarioId,
                execucao.getId(),
                minutosEfetivos
        );
        return new ResultadoConclusao(execucao, xp);
    }

    private int somarMinutosEfetivosConcluidos(UUID tarefaId, UUID usuarioId) {
        List<Object[]> linhas = execucaoRepository.listarConcluidasComDuracaoBlocoPorTarefa(
                tarefaId,
                usuarioId,
                StatusExecucao.CONCLUIDO
        );
        int soma = 0;
        for (Object[] linha : linhas) {
            Execucao e = (Execucao) linha[0];
            int duracaoBloco = ((Number) linha[1]).intValue();
            soma += XpMotor.minutosEfetivos(duracaoBloco, e.getIniciadoEm(), e.getConcluidoEm());
        }
        return soma;
    }

    @Transactional(readOnly = true)
    public ExecucoesDoDiaResponse listar(UUID usuarioId, LocalDate data) {
        LocalDate dataConsulta = data != null
                ? data
                : LocalDate.now(clock.withZone(PeriodoBRT.ZONE));

        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
                        usuarioId, dataConsulta, STATUS_CORRENTE)
                .orElse(null);

        // Corrente = maior versão RASCUNHO/APROVADO; só lista se essa corrente estiver APROVADO.
        if (corrente == null || corrente.getStatus() != StatusPlano.APROVADO) {
            return new ExecucoesDoDiaResponse(null, List.of());
        }

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(corrente.getId());
        Set<UUID> tarefaIds = blocos.stream()
                .map(BlocoPlano::getTarefaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> habitoIds = blocos.stream()
                .map(BlocoPlano::getHabitoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Tarefa> tarefas = tarefaIds.isEmpty()
                ? Map.of()
                : tarefaRepository.findAllById(tarefaIds).stream()
                        .collect(Collectors.toMap(Tarefa::getId, Function.identity()));
        Map<UUID, Habito> habitos = habitoIds.isEmpty()
                ? Map.of()
                : habitoRepository.findAllById(habitoIds).stream()
                        .collect(Collectors.toMap(Habito::getId, Function.identity()));

        Map<UUID, Execucao> execucoes = blocos.isEmpty()
                ? Map.of()
                : execucaoRepository
                        .findByUsuarioIdAndBlocoPlanoIdIn(
                                usuarioId,
                                blocos.stream().map(BlocoPlano::getId).toList()
                        ).stream()
                        .collect(Collectors.toMap(Execucao::getBlocoPlanoId, Function.identity()));

        Set<UUID> projetoIds = tarefas.values().stream()
                .map(Tarefa::getProjetoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> nomesProjeto = projetoIds.isEmpty()
                ? Map.of()
                : projetoRepository.findAllById(projetoIds).stream()
                        .collect(Collectors.toMap(Projeto::getId, Projeto::getNome));

        List<ItemExecucaoResponse> itens = blocos.stream()
                .map(bloco -> mapearItem(bloco, tarefas, habitos, nomesProjeto, execucoes))
                .toList();

        return new ExecucoesDoDiaResponse(
                PlanoAprovadoResumoResponse.from(corrente),
                itens
        );
    }

    private ItemExecucaoResponse mapearItem(
            BlocoPlano bloco,
            Map<UUID, Tarefa> tarefas,
            Map<UUID, Habito> habitos,
            Map<UUID, String> nomesProjeto,
            Map<UUID, Execucao> execucoes
    ) {
        Execucao execucao = execucoes.get(bloco.getId());
        if (bloco.getHabitoId() != null) {
            Habito habito = habitos.get(bloco.getHabitoId());
            if (habito == null) {
                throw new HabitoNaoEncontradoException();
            }
            return new ItemExecucaoResponse(
                    BlocoExecucaoResponse.from(bloco),
                    null,
                    HabitoResumoResponse.from(habito),
                    ExecucaoResponse.efetiva(execucao)
            );
        }
        Tarefa tarefa = tarefas.get(bloco.getTarefaId());
        if (tarefa == null) {
            throw new TarefaNaoEncontradaException();
        }
        String projetoNome = tarefa.getProjetoId() == null
                ? null
                : nomesProjeto.get(tarefa.getProjetoId());
        return new ItemExecucaoResponse(
                BlocoExecucaoResponse.from(bloco),
                TarefaResumoResponse.from(tarefa, projetoNome),
                ExecucaoResponse.efetiva(execucao)
        );
    }

    private Tarefa atualizarTarefaAposConcluir(UUID tarefaId, UUID usuarioId, Instant agora) {
        Tarefa tarefa = tarefaRepository.findByIdAndUsuarioId(tarefaId, usuarioId)
                .orElseThrow(TarefaNaoEncontradaException::new);

        if (STATUS_PARA_EM_PROGRESSO.contains(tarefa.getStatus())) {
            tarefa.atualizar(null, null, null, null, StatusTarefa.EM_PROGRESSO, null, agora);
        }

        long minutosConcluidos = execucaoRepository.somarDuracaoMinutosPorTarefaEStatus(
                tarefaId,
                usuarioId,
                StatusExecucao.CONCLUIDO
        );
        int minutosNecessarios = tarefa.getDuracaoMinutos();

        if (minutosConcluidos >= minutosNecessarios) {
            tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, agora);
        }

        return tarefaRepository.save(tarefa);
    }

    private void marcarTarefaEmProgressoSeAplicavel(BlocoPlano bloco, UUID usuarioId, Instant agora) {
        if (bloco.getTarefaId() == null) {
            return;
        }
        Tarefa tarefa = tarefaRepository.findByIdAndUsuarioId(bloco.getTarefaId(), usuarioId)
                .orElseThrow(TarefaNaoEncontradaException::new);

        if (!STATUS_PARA_EM_PROGRESSO.contains(tarefa.getStatus())) {
            return;
        }

        tarefa.atualizar(null, null, null, null, StatusTarefa.EM_PROGRESSO, null, agora);
        tarefaRepository.save(tarefa);
    }

    private BlocoPlano carregarBlocoDoUsuario(UUID usuarioId, UUID blocoId) {
        BlocoPlano bloco = blocoPlanoRepository.findById(blocoId)
                .orElseThrow(BlocoPlanoNaoEncontradoException::new);
        planoRepository.findByIdAndUsuarioId(bloco.getPlanoId(), usuarioId)
                .orElseThrow(BlocoPlanoNaoEncontradoException::new);
        return bloco;
    }

    private Execucao carregarExecucaoDoUsuario(UUID usuarioId, UUID execucaoId) {
        return execucaoRepository.findByIdAndUsuarioId(execucaoId, usuarioId)
                .orElseThrow(ExecucaoNaoEncontradaException::new);
    }

    private Plano garantirPlanoExecutavel(BlocoPlano bloco, UUID usuarioId) {
        Plano plano = planoRepository.findByIdAndUsuarioId(bloco.getPlanoId(), usuarioId)
                .orElseThrow(BlocoPlanoNaoEncontradoException::new);

        if (plano.getStatus() == StatusPlano.HABITO_AVULSO) {
            if (bloco.getHabitoId() == null || bloco.getTarefaId() != null) {
                throw new PlanoNaoExecutavelException();
            }
            return plano;
        }

        if (plano.getStatus() != StatusPlano.APROVADO) {
            throw new PlanoNaoExecutavelException();
        }

        // Corrente do dia = maior versão entre RASCUNHO/APROVADO (não o maior APROVADO isolado).
        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataAndStatusInOrderByVersaoDesc(
                        plano.getUsuarioId(), plano.getData(), STATUS_CORRENTE)
                .orElseThrow(PlanoNaoExecutavelException::new);

        if (!corrente.getId().equals(plano.getId())) {
            throw new PlanoNaoExecutavelException();
        }
        return plano;
    }
}
