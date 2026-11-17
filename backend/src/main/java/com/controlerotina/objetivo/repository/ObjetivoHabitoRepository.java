package com.controlerotina.objetivo.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.objetivo.entity.ObjetivoHabito;

public interface ObjetivoHabitoRepository extends JpaRepository<ObjetivoHabito, ObjetivoHabito.ObjetivoHabitoId> {

    List<ObjetivoHabito> findByHabitoIdIn(Collection<UUID> habitoIds);

    List<ObjetivoHabito> findByHabitoId(UUID habitoId);

    List<ObjetivoHabito> findByObjetivoId(UUID objetivoId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ObjetivoHabito oh where oh.habitoId = :habitoId")
    void deleteAllByHabitoId(@Param("habitoId") UUID habitoId);
}
