package com.controlerotina.tag.dto;

import java.util.UUID;

import com.controlerotina.tag.entity.Tag;

public record TagResumoResponse(
        UUID id,
        String nome,
        String cor
) {
    public static TagResumoResponse from(Tag tag) {
        return new TagResumoResponse(tag.getId(), tag.getNome(), tag.getCor());
    }
}
