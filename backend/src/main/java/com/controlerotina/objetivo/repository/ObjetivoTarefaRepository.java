package com.controlerotina.objetivo.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.objetivo.entity.ObjetivoTarefa;

public interface ObjetivoTarefaRepository extends JpaRepository<ObjetivoTarefa, ObjetivoTarefa.ObjetivoTarefaId> {

    List<ObjetivoTarefa> findByTarefaIdIn(Collection<UUID> tarefaIds);

    List<ObjetivoTarefa> findByTarefaId(UUID tarefaId);

    List<ObjetivoTarefa> findByObjetivoId(UUID objetivoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ObjetivoTarefa ot where ot.tarefaId = :tarefaId")
    void deleteAllByTarefaId(@Param("tarefaId") UUID tarefaId);
}
