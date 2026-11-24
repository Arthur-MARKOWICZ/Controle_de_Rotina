package com.controlerotina.objetivo.dto;

import org.jspecify.annotations.Nullable;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = AtualizarObjetivoRequest.Deserializer.class)
public record AtualizarObjetivoRequest(
        @Size(min = 1, max = 80)
        @Nullable
        String titulo,

        @Size(max = 500)
        @Nullable
        String descricao,

        @Nullable
        LocalDate prazo,

        @Schema(hidden = true)
        boolean prazoPresente
) {
    public AtualizarObjetivoRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null) {
            descricao = descricao.trim();
        }
    }

    public static class Deserializer extends ValueDeserializer<AtualizarObjetivoRequest> {

        @Override
        public AtualizarObjetivoRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws JacksonException {
            JsonNode node = p.readValueAsTree();
            boolean prazoPresente = node.has("prazo");
            LocalDate prazo = null;
            if (prazoPresente && !node.get("prazo").isNull()) {
                prazo = ctxt.readTreeAsValue(node.get("prazo"), LocalDate.class);
            }
            return new AtualizarObjetivoRequest(
                    texto(node, "titulo"),
                    texto(node, "descricao"),
                    prazo,
                    prazoPresente
            );
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
