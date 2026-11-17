package com.controlerotina.objetivo.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.objetivo.entity.ObjetivoProjeto;

public interface ObjetivoProjetoRepository extends JpaRepository<ObjetivoProjeto, ObjetivoProjeto.ObjetivoProjetoId> {

    List<ObjetivoProjeto> findByProjetoIdIn(Collection<UUID> projetoIds);

    List<ObjetivoProjeto> findByProjetoId(UUID projetoId);

    List<ObjetivoProjeto> findByObjetivoId(UUID objetivoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ObjetivoProjeto op where op.projetoId = :projetoId")
    void deleteAllByProjetoId(@Param("projetoId") UUID projetoId);
}
