package com.controlerotina.decidir.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = AtualizarDecidirRequest.Deserializer.class)
public record AtualizarDecidirRequest(
        @Size(min = 1, max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        boolean descricaoPresente,

        Instant decidirAte,

        boolean decidirAtePresente,

        UUID projetoId,

        boolean projetoIdPresente,

        List<UUID> tagIds,

        boolean tagIdsPresente
) {
    public AtualizarDecidirRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null && descricao.isBlank()) {
            descricao = null;
        } else if (descricao != null) {
            descricao = descricao.trim();
        }
    }

    public static class Deserializer extends ValueDeserializer<AtualizarDecidirRequest> {

        @Override
        public AtualizarDecidirRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws JacksonException {
            JsonNode node = p.readValueAsTree();
            boolean descricaoPresente = node.has("descricao");
            String descricao = texto(node, "descricao");
            boolean decidirAtePresente = node.has("decidirAte");
            Instant decidirAte = null;
            if (decidirAtePresente && !node.get("decidirAte").isNull()) {
                decidirAte = Instant.parse(node.get("decidirAte").asString());
            }
            boolean projetoIdPresente = node.has("projetoId");
            UUID projetoId = null;
            if (projetoIdPresente && !node.get("projetoId").isNull()) {
                projetoId = UUID.fromString(node.get("projetoId").asString());
            }
            boolean tagIdsPresente = node.has("tagIds");
            List<UUID> tagIds = null;
            if (tagIdsPresente) {
                tagIds = uuids(node.get("tagIds"));
            }
            return new AtualizarDecidirRequest(
                    texto(node, "titulo"),
                    descricao,
                    descricaoPresente,
                    decidirAte,
                    decidirAtePresente,
                    projetoId,
                    projetoIdPresente,
                    tagIds,
                    tagIdsPresente
            );
        }

        private static List<UUID> uuids(JsonNode valor) {
            if (valor == null || valor.isNull()) {
                return List.of();
            }
            List<UUID> ids = new ArrayList<>();
            for (JsonNode item : valor) {
                if (item != null && !item.isNull()) {
                    ids.add(UUID.fromString(item.asString()));
                }
            }
            return ids;
        }

        private static String texto(JsonNode node, String campo) {
            JsonNode valor = node.get(campo);
            if (valor == null || valor.isNull()) {
                return null;
            }
            return valor.asString();
        }
    }
}
