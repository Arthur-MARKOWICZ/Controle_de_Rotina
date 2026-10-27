package com.controlerotina.execucao.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.controlerotina.execucao.entity.Execucao;

public interface ExecucaoRepository extends JpaRepository<Execucao, UUID> {

    Optional<Execucao> findByBlocoPlanoId(UUID blocoPlanoId);

    Optional<Execucao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    boolean existsByBlocoPlanoId(UUID blocoPlanoId);
}
