package com.controlerotina.tag.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.controlerotina.tag.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByUsuarioIdOrderByNomeAsc(UUID usuarioId);

    Optional<Tag> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    boolean existsByUsuarioIdAndNomeNormalizado(UUID usuarioId, String nomeNormalizado);

    boolean existsByUsuarioIdAndNomeNormalizadoAndIdNot(UUID usuarioId, String nomeNormalizado, UUID id);

    List<Tag> findByUsuarioIdAndIdIn(UUID usuarioId, Collection<UUID> ids);
}
