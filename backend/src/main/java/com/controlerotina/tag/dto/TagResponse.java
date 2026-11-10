package com.controlerotina.tag.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.tag.entity.Tag;

public record TagResponse(
        UUID id,
        String nome,
        String cor,
        Instant createdAt,
        Instant updatedAt
) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getNome(),
                tag.getCor(),
                tag.getCreatedAt(),
                tag.getUpdatedAt()
        );
    }
}
