package com.controlerotina.tag.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.controlerotina.tag.entity.HabitoTag;

public interface HabitoTagRepository extends JpaRepository<HabitoTag, HabitoTag.HabitoTagId> {

    List<HabitoTag> findByHabitoIdIn(Collection<UUID> habitoIds);

    List<HabitoTag> findByHabitoId(UUID habitoId);

    void deleteByTagId(UUID tagId);

    @Query("""
            select distinct ht.habitoId from HabitoTag ht
            where ht.tagId in :tagIds
            """)
    List<UUID> findHabitoIdsComAlgumaTag(@Param("tagIds") Collection<UUID> tagIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from HabitoTag ht where ht.habitoId = :habitoId")
    void deleteAllByHabitoId(@Param("habitoId") UUID habitoId);
}
