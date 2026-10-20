package com.controlerotina.planner.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.controlerotina.planner.entity.BlocoPlano;

public interface BlocoPlanoRepository extends JpaRepository<BlocoPlano, UUID> {

    List<BlocoPlano> findByPlanoIdOrderByOrdemAsc(UUID planoId);

    Optional<BlocoPlano> findByIdAndPlanoId(UUID id, UUID planoId);

    boolean existsByPlanoIdAndTarefaId(UUID planoId, UUID tarefaId);
}
