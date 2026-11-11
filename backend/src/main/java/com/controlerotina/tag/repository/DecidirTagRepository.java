package com.controlerotina.tag.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.tag.entity.DecidirTag;

public interface DecidirTagRepository extends JpaRepository<DecidirTag, DecidirTag.DecidirTagId> {

    List<DecidirTag> findByItemDecidirIdIn(Collection<UUID> itemDecidirIds);

    List<DecidirTag> findByItemDecidirId(UUID itemDecidirId);

    void deleteByTagId(UUID tagId);

    @Query("""
            select distinct dt.itemDecidirId from DecidirTag dt
            where dt.tagId in :tagIds
            """)
    List<UUID> findItemDecidirIdsComAlgumaTag(@Param("tagIds") Collection<UUID> tagIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DecidirTag dt where dt.itemDecidirId = :itemDecidirId")
    void deleteAllByItemDecidirId(@Param("itemDecidirId") UUID itemDecidirId);
}
