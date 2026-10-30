package com.controlerotina.projeto.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.controlerotina.projeto.dto.AtualizarProjetoRequest;
import com.controlerotina.projeto.dto.CriarProjetoRequest;
import com.controlerotina.projeto.dto.ProjetoResponse;
import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;
import com.controlerotina.projeto.repository.ProjetoRepository;
import com.controlerotina.shared.exception.ProjetoJaArquivadoException;
import com.controlerotina.shared.exception.ProjetoJaAtivoException;
import com.controlerotina.shared.exception.ProjetoNaoEncontradoException;

@Service
public class ProjetoService {

    private final ProjetoRepository projetoRepository;
    private final Clock clock;

    public ProjetoService(ProjetoRepository projetoRepository, Clock clock) {
        this.projetoRepository = projetoRepository;
        this.clock = clock;
    }

    @Transactional
    public ProjetoResponse criar(UUID usuarioId, CriarProjetoRequest request) {
        Instant agora = clock.instant();
        Projeto projeto = new Projeto(
                UUID.randomUUID(),
                usuarioId,
                request.nome(),
                request.descricao(),
                StatusProjeto.ATIVO,
                agora,
                agora
        );
        return ProjetoResponse.from(projetoRepository.save(projeto));
    }

    @Transactional(readOnly = true)
    public Page<ProjetoResponse> listar(UUID usuarioId, StatusProjeto status, Pageable pageable) {
        StatusProjeto filtro = status != null ? status : StatusProjeto.ATIVO;
        return projetoRepository.findByUsuarioIdAndStatus(usuarioId, filtro, pageable)
                .map(ProjetoResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjetoResponse buscarPorId(UUID usuarioId, UUID id) {
        return ProjetoResponse.from(buscarDoUsuario(usuarioId, id));
    }

    @Transactional
    public ProjetoResponse atualizar(UUID usuarioId, UUID id, AtualizarProjetoRequest request) {
        Projeto projeto = buscarDoUsuario(usuarioId, id);
        projeto.atualizar(request.nome(), request.descricao(), clock.instant());
        return ProjetoResponse.from(projeto);
    }

    @Transactional
    public ProjetoResponse arquivar(UUID usuarioId, UUID id) {
        Projeto projeto = buscarDoUsuario(usuarioId, id);
        if (projeto.getStatus() == StatusProjeto.ARQUIVADO) {
            throw new ProjetoJaArquivadoException();
        }
        projeto.arquivar(clock.instant());
        return ProjetoResponse.from(projeto);
    }

    @Transactional
    public ProjetoResponse reativar(UUID usuarioId, UUID id) {
        Projeto projeto = buscarDoUsuario(usuarioId, id);
        if (projeto.getStatus() == StatusProjeto.ATIVO) {
            throw new ProjetoJaAtivoException();
        }
        projeto.reativar(clock.instant());
        return ProjetoResponse.from(projeto);
    }

    private Projeto buscarDoUsuario(UUID usuarioId, UUID id) {
        return projetoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(ProjetoNaoEncontradoException::new);
    }
}
