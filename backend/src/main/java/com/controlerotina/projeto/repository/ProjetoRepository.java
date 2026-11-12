package com.controlerotina.projeto.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.projeto.entity.Projeto;
import com.controlerotina.projeto.entity.StatusProjeto;

public interface ProjetoRepository extends JpaRepository<Projeto, UUID>, JpaSpecificationExecutor<Projeto> {

    Optional<Projeto> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    Page<Projeto> findByUsuarioIdAndStatus(UUID usuarioId, StatusProjeto status, Pageable pageable);

    List<Projeto> findByUsuarioIdOrderByOrdemAscCreatedAtAsc(UUID usuarioId);

    @Query("""
            SELECT COALESCE(MAX(p.ordem), -1)
              FROM Projeto p
             WHERE p.usuarioId = :usuarioId
            """)
    int findMaxOrdem(@Param("usuarioId") UUID usuarioId);
}
