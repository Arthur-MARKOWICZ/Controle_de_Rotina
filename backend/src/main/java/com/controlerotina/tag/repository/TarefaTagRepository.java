package com.controlerotina.tag.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.tag.entity.TarefaTag;

public interface TarefaTagRepository extends JpaRepository<TarefaTag, TarefaTag.TarefaTagId> {

    List<TarefaTag> findByTarefaIdIn(Collection<UUID> tarefaIds);

    List<TarefaTag> findByTarefaId(UUID tarefaId);

    void deleteByTarefaId(UUID tarefaId);

    void deleteByTagId(UUID tagId);

    @Query("""
            select tt.tarefaId from TarefaTag tt
            where tt.tagId in :tagIds
            group by tt.tarefaId
            having count(distinct tt.tagId) = :qtd
            """)
    List<UUID> findTarefaIdsComTodasTags(
            @Param("tagIds") Collection<UUID> tagIds,
            @Param("qtd") long qtd
    );

    @Query("""
            select distinct tt.tarefaId from TarefaTag tt
            where tt.tagId in :tagIds
            """)
    List<UUID> findTarefaIdsComAlgumaTag(@Param("tagIds") Collection<UUID> tagIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TarefaTag tt where tt.tarefaId = :tarefaId")
    void deleteAllByTarefaId(@Param("tarefaId") UUID tarefaId);
}
