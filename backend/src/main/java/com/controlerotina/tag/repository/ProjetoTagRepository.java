package com.controlerotina.tag.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.tag.entity.ProjetoTag;

public interface ProjetoTagRepository extends JpaRepository<ProjetoTag, ProjetoTag.ProjetoTagId> {

    List<ProjetoTag> findByProjetoIdIn(Collection<UUID> projetoIds);

    List<ProjetoTag> findByProjetoId(UUID projetoId);

    void deleteByTagId(UUID tagId);

    @Query("""
            select distinct pt.projetoId from ProjetoTag pt
            where pt.tagId in :tagIds
            """)
    List<UUID> findProjetoIdsComAlgumaTag(@Param("tagIds") Collection<UUID> tagIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ProjetoTag pt where pt.projetoId = :projetoId")
    void deleteAllByProjetoId(@Param("projetoId") UUID projetoId);
}
