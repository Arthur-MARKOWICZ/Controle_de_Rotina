package com.controlerotina.projeto.dto;

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

@JsonDeserialize(using = AtualizarProjetoRequest.Deserializer.class)
public record AtualizarProjetoRequest(
        @Size(min = 1, max = 80)
        String nome,

        @Size(max = 500)
        String descricao,

        List<UUID> tagIds,

        boolean tagIdsPresente
) {
    public AtualizarProjetoRequest {
        if (nome != null) {
            nome = nome.trim();
        }
        if (descricao != null) {
            descricao = descricao.trim();
        }
    }

    public static class Deserializer extends ValueDeserializer<AtualizarProjetoRequest> {

        @Override
        public AtualizarProjetoRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws JacksonException {
            JsonNode node = p.readValueAsTree();
            boolean tagIdsPresente = node.has("tagIds");
            List<UUID> tagIds = null;
            if (tagIdsPresente) {
                tagIds = uuids(node.get("tagIds"));
            }
            return new AtualizarProjetoRequest(
                    texto(node, "nome"),
                    texto(node, "descricao"),
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
