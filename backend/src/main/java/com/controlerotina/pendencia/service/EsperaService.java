package com.controlerotina.pendencia.service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.pendencia.dto.CriarEsperaRequest;
import com.controlerotina.pendencia.dto.EsperaResumo;
import com.controlerotina.pendencia.dto.EsperasDeOrigem;
import com.controlerotina.pendencia.entity.Espera;
import com.controlerotina.pendencia.entity.TipoRecursoEspera;
import com.controlerotina.pendencia.repository.EsperaRepository;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.AutoEsperaException;
import com.controlerotina.shared.exception.CicloEsperaException;
import com.controlerotina.shared.exception.EsperaDuplicadaException;
import com.controlerotina.shared.exception.EsperaNaoEncontradaException;
import com.controlerotina.shared.exception.ProjetoArquivadoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;
import com.controlerotina.shared.exception.TarefaBloqueadaException;
import com.controlerotina.shared.exception.TarefaNaoEncontradaException;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.entity.Tarefa;
import com.controlerotina.tarefa.repository.TarefaRepository;

@Service
public class EsperaService {

    private final EsperaRepository esperaRepository;
    private final TarefaRepository tarefaRepository;
    private final ProjetoRepository projetoRepository;
    private final Clock clock;

    public EsperaService(
            EsperaRepository esperaRepository,
            TarefaRepository tarefaRepository,
            ProjetoRepository projetoRepository,
            Clock clock
    ) {
        this.esperaRepository = esperaRepository;
        this.tarefaRepository = tarefaRepository;
        this.projetoRepository = projetoRepository;
        this.clock = clock;
    }

    @Transactional
    public EsperaResumo criar(
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            UUID origemId,
            CriarEsperaRequest request
    ) {
        garantirOrigem(usuarioId, origemTipo, origemId, true);
        if (origemTipo == request.alvoTipo() && origemId.equals(request.alvoId())) {
            throw new AutoEsperaException();
        }
        String alvoTitulo = tituloDoAlvo(usuarioId, request.alvoTipo(), request.alvoId(), true);
        if (esperaRepository.existsByUsuarioIdAndOrigemTipoAndOrigemIdAndAlvoTipoAndAlvoId(
                usuarioId, origemTipo, origemId, request.alvoTipo(), request.alvoId())) {
            throw new EsperaDuplicadaException();
        }
        garantirSemCiclo(usuarioId, origemTipo, origemId, request.alvoTipo(), request.alvoId());

        Espera espera = new Espera(
                UUID.randomUUID(),
                usuarioId,
                origemTipo,
                origemId,
                request.alvoTipo(),
                request.alvoId(),
                clock.instant()
        );
        return EsperaResumo.from(
                esperaRepository.save(espera),
                alvoTitulo,
                alvoResolvido(usuarioId, request.alvoTipo(), request.alvoId())
        );
    }

    @Transactional(readOnly = true)
    public List<EsperaResumo> listar(UUID usuarioId, TipoRecursoEspera origemTipo, UUID origemId) {
        garantirOrigem(usuarioId, origemTipo, origemId, false);
        List<Espera> esperas = esperaRepository
                .findByUsuarioIdAndOrigemTipoAndOrigemIdOrderByCreatedAtAsc(usuarioId, origemTipo, origemId);
        Map<RecursoEspera, Alvo> alvos = resolverAlvos(esperas);
        return esperas.stream().map(espera -> toResumo(espera, alvos)).toList();
    }

    @Transactional
    public void excluir(UUID usuarioId, TipoRecursoEspera origemTipo, UUID origemId, UUID esperaId) {
        garantirOrigem(usuarioId, origemTipo, origemId, false);
        Espera espera = esperaRepository
                .findByIdAndUsuarioIdAndOrigemTipoAndOrigemId(esperaId, usuarioId, origemTipo, origemId)
                .orElseThrow(EsperaNaoEncontradaException::new);
        esperaRepository.delete(espera);
    }

    @Transactional
    public void apagarPorRecurso(UUID usuarioId, TipoRecursoEspera tipo, UUID id) {
        esperaRepository.deleteByUsuarioIdAndOrigemTipoAndOrigemId(usuarioId, tipo, id);
        esperaRepository.deleteByUsuarioIdAndAlvoTipoAndAlvoId(usuarioId, tipo, id);
    }

    /**
     * Esperas e bloqueio das tarefas informadas, em lote (sem N+1).
     * Uma tarefa está bloqueada quando tem espera irresolvida própria, quando o pai tem,
     * ou quando o projeto dela é origem de espera irresolvida.
     */
    @Transactional(readOnly = true)
    public EsperasDeOrigem enriquecerTarefas(UUID usuarioId, Collection<Tarefa> tarefas) {
        if (tarefas.isEmpty()) {
            return EsperasDeOrigem.vazio();
        }
        Set<UUID> origensTarefa = new HashSet<>();
        Set<UUID> origensProjeto = new HashSet<>();
        for (Tarefa tarefa : tarefas) {
            origensTarefa.add(tarefa.getId());
            if (tarefa.getParentId() != null) {
                origensTarefa.add(tarefa.getParentId());
            }
            if (tarefa.getProjetoId() != null) {
                origensProjeto.add(tarefa.getProjetoId());
            }
        }

        List<Espera> esperas = new ArrayList<>(
                buscarPorOrigens(usuarioId, TipoRecursoEspera.TAREFA, origensTarefa));
        esperas.addAll(buscarPorOrigens(usuarioId, TipoRecursoEspera.PROJETO, origensProjeto));
        Map<RecursoEspera, Alvo> alvos = resolverAlvos(esperas);
        Set<RecursoEspera> irresolvidas = origensIrresolvidas(esperas, alvos);

        Map<UUID, List<EsperaResumo>> porOrigem = resumosPorOrigem(
                esperas, alvos, TipoRecursoEspera.TAREFA);
        Set<UUID> bloqueadas = new HashSet<>();
        for (Tarefa tarefa : tarefas) {
            if (bloqueada(tarefa, irresolvidas)) {
                bloqueadas.add(tarefa.getId());
            }
        }
        return new EsperasDeOrigem(porOrigem, bloqueadas);
    }

    /** Esperas e bloqueio dos projetos informados, em lote (sem N+1). */
    @Transactional(readOnly = true)
    public EsperasDeOrigem enriquecerProjetos(UUID usuarioId, Collection<UUID> projetoIds) {
        Set<UUID> origens = projetoIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (origens.isEmpty()) {
            return EsperasDeOrigem.vazio();
        }
        List<Espera> esperas = buscarPorOrigens(usuarioId, TipoRecursoEspera.PROJETO, origens);
        Map<RecursoEspera, Alvo> alvos = resolverAlvos(esperas);
        Set<UUID> bloqueadas = origensIrresolvidas(esperas, alvos).stream()
                .map(RecursoEspera::id)
                .collect(Collectors.toSet());
        return new EsperasDeOrigem(
                resumosPorOrigem(esperas, alvos, TipoRecursoEspera.PROJETO),
                bloqueadas
        );
    }

    @Transactional(readOnly = true)
    public Set<UUID> idsTarefasBloqueadas(UUID usuarioId, Collection<Tarefa> tarefas) {
        return enriquecerTarefas(usuarioId, tarefas).bloqueadas();
    }

    @Transactional(readOnly = true)
    public boolean tarefaBloqueada(UUID usuarioId, Tarefa tarefa) {
        return enriquecerTarefas(usuarioId, List.of(tarefa)).bloqueada(tarefa.getId());
    }

    public void garantirTarefaNaoBloqueada(UUID usuarioId, Tarefa tarefa) {
        if (tarefaBloqueada(usuarioId, tarefa)) {
            throw new TarefaBloqueadaException(tarefa.getId());
        }
    }

    private static boolean bloqueada(Tarefa tarefa, Set<RecursoEspera> irresolvidas) {
        if (irresolvidas.contains(new RecursoEspera(TipoRecursoEspera.TAREFA, tarefa.getId()))) {
            return true;
        }
        if (tarefa.getParentId() != null
                && irresolvidas.contains(new RecursoEspera(TipoRecursoEspera.TAREFA, tarefa.getParentId()))) {
            return true;
        }
        return tarefa.getProjetoId() != null
                && irresolvidas.contains(new RecursoEspera(TipoRecursoEspera.PROJETO, tarefa.getProjetoId()));
    }

    private List<Espera> buscarPorOrigens(UUID usuarioId, TipoRecursoEspera tipo, Set<UUID> origemIds) {
        if (origemIds.isEmpty()) {
            return List.of();
        }
        return esperaRepository.findByUsuarioIdAndOrigemTipoAndOrigemIdIn(usuarioId, tipo, origemIds);
    }

    private static Set<RecursoEspera> origensIrresolvidas(
            List<Espera> esperas,
            Map<RecursoEspera, Alvo> alvos
    ) {
        Set<RecursoEspera> irresolvidas = new HashSet<>();
        for (Espera espera : esperas) {
            if (!alvoDe(espera, alvos).resolvida()) {
                irresolvidas.add(new RecursoEspera(espera.getOrigemTipo(), espera.getOrigemId()));
            }
        }
        return irresolvidas;
    }

    private static Map<UUID, List<EsperaResumo>> resumosPorOrigem(
            List<Espera> esperas,
            Map<RecursoEspera, Alvo> alvos,
            TipoRecursoEspera origemTipo
    ) {
        Map<UUID, List<EsperaResumo>> porOrigem = new LinkedHashMap<>();
        esperas.stream()
                .filter(espera -> espera.getOrigemTipo() == origemTipo)
                .sorted(java.util.Comparator.comparing(Espera::getCreatedAt))
                .forEach(espera -> porOrigem
                        .computeIfAbsent(espera.getOrigemId(), ignorado -> new ArrayList<>())
                        .add(toResumo(espera, alvos)));
        return porOrigem;
    }

    private static EsperaResumo toResumo(Espera espera, Map<RecursoEspera, Alvo> alvos) {
        Alvo alvo = alvoDe(espera, alvos);
        return EsperaResumo.from(espera, alvo.titulo(), alvo.resolvida());
    }

    private static Alvo alvoDe(Espera espera, Map<RecursoEspera, Alvo> alvos) {
        return alvos.getOrDefault(
                new RecursoEspera(espera.getAlvoTipo(), espera.getAlvoId()),
                Alvo.AUSENTE
        );
    }

    /**
     * Título e resolução de cada alvo citado pelas esperas, em quatro consultas no máximo.
     * Alvo que não existe mais conta como irresolvido (a limpeza de vínculo já apaga esses casos).
     */
    private Map<RecursoEspera, Alvo> resolverAlvos(List<Espera> esperas) {
        if (esperas.isEmpty()) {
            return Map.of();
        }
        Set<UUID> idsTarefa = idsDeAlvo(esperas, TipoRecursoEspera.TAREFA);
        Set<UUID> idsProjeto = idsDeAlvo(esperas, TipoRecursoEspera.PROJETO);

        Map<RecursoEspera, Alvo> alvos = new HashMap<>();
        if (!idsTarefa.isEmpty()) {
            for (Tarefa tarefa : tarefaRepository.findAllById(idsTarefa)) {
                boolean resolvida = tarefa.getStatus() == StatusTarefa.FINALIZADA
                        && tarefa.getDeletedAt() == null;
                alvos.put(
                        new RecursoEspera(TipoRecursoEspera.TAREFA, tarefa.getId()),
                        new Alvo(tarefa.getTitulo(), resolvida)
                );
            }
        }
        if (!idsProjeto.isEmpty()) {
            Map<UUID, List<Tarefa>> tarefasPorProjeto = tarefaRepository
                    .findByProjetoIdInAndDeletedAtIsNull(idsProjeto).stream()
                    .collect(Collectors.groupingBy(Tarefa::getProjetoId));
            for (Projeto projeto : projetoRepository.findAllById(idsProjeto)) {
                alvos.put(
                        new RecursoEspera(TipoRecursoEspera.PROJETO, projeto.getId()),
                        new Alvo(projeto.getNome(), projetoResolvido(
                                tarefasPorProjeto.getOrDefault(projeto.getId(), List.of())))
                );
            }
        }
        return alvos;
    }

    private static Set<UUID> idsDeAlvo(List<Espera> esperas, TipoRecursoEspera tipo) {
        return esperas.stream()
                .filter(espera -> espera.getAlvoTipo() == tipo)
                .map(Espera::getAlvoId)
                .collect(Collectors.toSet());
    }

    private static boolean projetoResolvido(List<Tarefa> tarefasDoProjeto) {
        return !tarefasDoProjeto.isEmpty()
                && tarefasDoProjeto.stream().noneMatch(tarefa -> statusAberto(tarefa.getStatus()));
    }

    private record Alvo(String titulo, boolean resolvida) {
        private static final Alvo AUSENTE = new Alvo(null, false);
    }

    private void garantirSemCiclo(
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            UUID origemId,
            TipoRecursoEspera alvoTipo,
            UUID alvoId
    ) {
        RecursoEspera origem = new RecursoEspera(origemTipo, origemId);
        RecursoEspera alvo = new RecursoEspera(alvoTipo, alvoId);
        Map<RecursoEspera, List<RecursoEspera>> adjacencia = new HashMap<>();
        for (Espera espera : esperaRepository.findByUsuarioId(usuarioId)) {
            RecursoEspera de = new RecursoEspera(espera.getOrigemTipo(), espera.getOrigemId());
            RecursoEspera para = new RecursoEspera(espera.getAlvoTipo(), espera.getAlvoId());
            adjacencia.computeIfAbsent(de, ignorado -> new ArrayList<>()).add(para);
        }
        Deque<RecursoEspera> fila = new ArrayDeque<>();
        Set<RecursoEspera> visitados = new HashSet<>();
        fila.add(alvo);
        visitados.add(alvo);
        while (!fila.isEmpty()) {
            RecursoEspera atual = fila.removeFirst();
            if (atual.equals(origem)) {
                throw new CicloEsperaException();
            }
            for (RecursoEspera proximo : adjacencia.getOrDefault(atual, List.of())) {
                if (visitados.add(proximo)) {
                    fila.add(proximo);
                }
            }
        }
    }

    private record RecursoEspera(TipoRecursoEspera tipo, UUID id) {
    }

    private void garantirOrigem(
            UUID usuarioId,
            TipoRecursoEspera origemTipo,
            UUID origemId,
            boolean exigirAtivo
    ) {
        if (origemTipo == TipoRecursoEspera.TAREFA) {
            garantirTarefa(usuarioId, origemId);
            return;
        }
        garantirProjeto(usuarioId, origemId, exigirAtivo);
    }

    private String tituloDoAlvo(
            UUID usuarioId,
            TipoRecursoEspera alvoTipo,
            UUID alvoId,
            boolean exigirAtivo
    ) {
        if (alvoTipo == TipoRecursoEspera.TAREFA) {
            return garantirTarefa(usuarioId, alvoId).getTitulo();
        }
        return garantirProjeto(usuarioId, alvoId, exigirAtivo).getNome();
    }

    private boolean alvoResolvido(UUID usuarioId, TipoRecursoEspera alvoTipo, UUID alvoId) {
        if (alvoTipo == TipoRecursoEspera.TAREFA) {
            Tarefa alvo = garantirTarefa(usuarioId, alvoId);
            return alvo.getStatus() == StatusTarefa.FINALIZADA && alvo.getDeletedAt() == null;
        }
        return projetoResolvido(tarefaRepository.findByProjetoIdAndDeletedAtIsNull(alvoId));
    }

    private static boolean statusAberto(StatusTarefa status) {
        return status == StatusTarefa.PENDENTE
                || status == StatusTarefa.EM_PROGRESSO
                || status == StatusTarefa.ATRASADA;
    }

    private Tarefa garantirTarefa(UUID usuarioId, UUID tarefaId) {
        Tarefa tarefa = tarefaRepository.findByIdAndUsuarioId(tarefaId, usuarioId)
                .orElseThrow(TarefaNaoEncontradaException::new);
        if (tarefa.getDeletedAt() != null) {
            throw new TarefaNaoEncontradaException();
        }
        return tarefa;
    }

    private Projeto garantirProjeto(UUID usuarioId, UUID projetoId, boolean exigirAtivo) {
        Projeto projeto = projetoRepository.findByIdAndUsuarioId(projetoId, usuarioId)
                .orElseThrow(ProjetoNaoEncontradoException::new);
        if (exigirAtivo && projeto.getStatus() == StatusProjeto.ARQUIVADO) {
            throw new ProjetoArquivadoException();
        }
        return projeto;
    }
}
