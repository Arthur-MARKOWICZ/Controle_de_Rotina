package com.controlerotina.projeto.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;

public interface ProjetoRepository extends JpaRepository<Projeto, UUID> {

    Optional<Projeto> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Page<Projeto> findByUsuarioIdAndStatus(UUID usuarioId, StatusProjeto status, Pageable pageable);
}
