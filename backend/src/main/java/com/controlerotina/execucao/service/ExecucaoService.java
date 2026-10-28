package com.controlerotina.execucao.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.controlerotina.planner.dto.TarefaResumoResponse;
import com.controlerotina.planner.entity.BlocoPlano;
import com.controlerotina.planner.entity.Plano;
import com.controlerotina.planner.entity.StatusPlano;
import com.controlerotina.planner.repository.BlocoPlanoRepository;
import com.controlerotina.planner.repository.PlanoRepository;
import com.controlerotina.shared.exception.BlocoPlanoNaoEncontradoException;
import com.controlerotina.shared.exception.ExecucaoJaConcluidaException;
import com.controlerotina.shared.exception.ExecucaoJaEmAndamentoException;
import com.controlerotina.shared.exception.ExecucaoNaoEmAndamentoException;
import com.controlerotina.shared.exception.ExecucaoNaoEncontradaException;
import com.controlerotina.shared.exception.PlanoNaoExecutavelException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;
import com.controlerotina.xp.service.XpConcessaoService;

@Service
public class ExecucaoService {

    static final ZoneId ZONE_SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    private static final Set<StatusTarefa> STATUS_PARA_EM_PROGRESSO = Set.of(
            StatusTarefa.PENDENTE,
            StatusTarefa.ATRASADA
    );

    private final ExecucaoRepository execucaoRepository;
    private final BlocoPlanoRepository blocoPlanoRepository;
    private final PlanoRepository planoRepository;
    private final TarefaRepository tarefaRepository;
    private final XpConcessaoService xpConcessaoService;
    private final Clock clock;

    public ExecucaoService(
            ExecucaoRepository execucaoRepository,
            BlocoPlanoRepository blocoPlanoRepository,
            PlanoRepository planoRepository,
            TarefaRepository tarefaRepository,
            XpConcessaoService xpConcessaoService,
            Clock clock
    ) {
        this.execucaoRepository = execucaoRepository;
        this.blocoPlanoRepository = blocoPlanoRepository;
        this.planoRepository = planoRepository;
        this.tarefaRepository = tarefaRepository;
        this.xpConcessaoService = xpConcessaoService;
        this.clock = clock;
    }

    @Transactional
    public ResultadoInicio iniciar(UUID usuarioId, UUID blocoId) {
        BlocoPlano bloco = carregarBlocoDoUsuario(usuarioId, blocoId);
        garantirPlanoAprovadoCorrente(bloco.getPlanoId(), usuarioId);

        Instant agora = clock.instant();
        var existente = execucaoRepository.findByBlocoPlanoId(bloco.getId());

        if (existente.isEmpty()) {
            Execucao criada = execucaoRepository.save(Execucao.iniciarNova(
                    UUID.randomUUID(),
                    usuarioId,
                    bloco.getId(),
                    agora
            ));
            marcarTarefaEmProgressoSeAplicavel(bloco.getTarefaId(), usuarioId, agora);
            return new ResultadoInicio(criada, true);
        }

        Execucao execucao = existente.get();
        return switch (execucao.getStatus()) {
            case EM_ANDAMENTO -> throw new ExecucaoJaEmAndamentoException();
            case CONCLUIDO -> throw new ExecucaoJaConcluidaException();
            case ABORTADO -> {
                execucao.reiniciar(agora);
                Execucao salva = execucaoRepository.save(execucao);
                marcarTarefaEmProgressoSeAplicavel(bloco.getTarefaId(), usuarioId, agora);
                yield new ResultadoInicio(salva, false);
            }
            case NAO_INICIADO -> {
                execucao.reiniciar(agora);
                Execucao salva = execucaoRepository.save(execucao);
                marcarTarefaEmProgressoSeAplicavel(bloco.getTarefaId(), usuarioId, agora);
                yield new ResultadoInicio(salva, false);
            }
        };
    }

    @Transactional
    public Execucao abortar(UUID usuarioId, UUID execucaoId) {
        Execucao execucao = carregarExecucaoDoUsuario(usuarioId, execucaoId);
        BlocoPlano bloco = carregarBlocoDoUsuario(usuarioId, execucao.getBlocoPlanoId());
        garantirPlanoAprovadoCorrente(bloco.getPlanoId(), usuarioId);

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
        garantirPlanoAprovadoCorrente(bloco.getPlanoId(), usuarioId);

        if (execucao.getStatus() != StatusExecucao.EM_ANDAMENTO) {
            throw new ExecucaoNaoEmAndamentoException();
        }

        Instant agora = clock.instant();
        execucao.concluir(agora);
        execucaoRepository.save(execucao);

        Tarefa tarefa = atualizarTarefaAposConcluir(bloco.getTarefaId(), usuarioId, agora);
        XpConcessaoService.ResultadoConcessao xp = xpConcessaoService.concederAoConcluir(
                usuarioId,
                execucao.getId(),
                bloco.getDuracaoMinutos(),
                tarefa
        );
        return new ResultadoConclusao(execucao, xp);
    }

    @Transactional(readOnly = true)
    public ExecucoesDoDiaResponse listar(UUID usuarioId, LocalDate data) {
        LocalDate dataConsulta = data != null
                ? data
                : LocalDate.now(clock.withZone(ZONE_SAO_PAULO));

        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataOrderByVersaoDesc(usuarioId, dataConsulta)
                .orElse(null);

        if (corrente == null || corrente.getStatus() != StatusPlano.APROVADO) {
            return new ExecucoesDoDiaResponse(null, List.of());
        }

        List<BlocoPlano> blocos = blocoPlanoRepository.findByPlanoIdOrderByOrdemAsc(corrente.getId());
        Map<UUID, Tarefa> tarefas = tarefaRepository.findAllById(
                        blocos.stream().map(BlocoPlano::getTarefaId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(Tarefa::getId, Function.identity()));

        Map<UUID, Execucao> execucoes = blocos.isEmpty()
                ? Map.of()
                : execucaoRepository
                        .findByUsuarioIdAndBlocoPlanoIdIn(
                                usuarioId,
                                blocos.stream().map(BlocoPlano::getId).toList()
                        ).stream()
                        .collect(Collectors.toMap(Execucao::getBlocoPlanoId, Function.identity()));

        List<ItemExecucaoResponse> itens = blocos.stream()
                .map(bloco -> {
                    Tarefa tarefa = tarefas.get(bloco.getTarefaId());
                    if (tarefa == null) {
                        throw new TarefaNaoEncontradaException();
                    }
                    Execucao execucao = execucoes.get(bloco.getId());
                    return new ItemExecucaoResponse(
                            BlocoExecucaoResponse.from(bloco),
                            TarefaResumoResponse.from(tarefa),
                            ExecucaoResponse.efetiva(execucao)
                    );
                })
                .toList();

        return new ExecucoesDoDiaResponse(
                PlanoAprovadoResumoResponse.from(corrente),
                itens
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
        int minutosNecessarios = tarefa.getDuracaoHoras() * 60;

        if (minutosConcluidos >= minutosNecessarios) {
            tarefa.atualizar(null, null, null, null, StatusTarefa.FINALIZADA, null, agora);
        }

        return tarefaRepository.save(tarefa);
    }

    private void marcarTarefaEmProgressoSeAplicavel(UUID tarefaId, UUID usuarioId, Instant agora) {
        Tarefa tarefa = tarefaRepository.findByIdAndUsuarioId(tarefaId, usuarioId)
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

    private void garantirPlanoAprovadoCorrente(UUID planoId, UUID usuarioId) {
        Plano plano = planoRepository.findByIdAndUsuarioId(planoId, usuarioId)
                .orElseThrow(BlocoPlanoNaoEncontradoException::new);

        if (plano.getStatus() != StatusPlano.APROVADO) {
            throw new PlanoNaoExecutavelException();
        }

        Plano corrente = planoRepository
                .findFirstByUsuarioIdAndDataOrderByVersaoDesc(plano.getUsuarioId(), plano.getData())
                .orElseThrow(PlanoNaoExecutavelException::new);

        if (!corrente.getId().equals(plano.getId())) {
            throw new PlanoNaoExecutavelException();
        }
    }
}
