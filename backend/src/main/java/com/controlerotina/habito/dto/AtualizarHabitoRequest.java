package com.controlerotina.habito.dto;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.controlerotina.capacidade.entity.DiaSemana;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = AtualizarHabitoRequest.Deserializer.class)
public record AtualizarHabitoRequest(
        @Size(min = 1, max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        @DuracaoMinutosValida
        Integer duracaoMinutos,

        @Size(min = 1)
        Set<DiaSemana> diasSemana,

        LocalTime horarioPreferencial,

        boolean horarioPreferencialPresente,

        List<UUID> tagIds,

        boolean tagIdsPresente
) {
    public AtualizarHabitoRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null && descricao.isBlank()) {
            descricao = null;
        } else if (descricao != null) {
            descricao = descricao.trim();
        }
    }

    public static class Deserializer extends ValueDeserializer<AtualizarHabitoRequest> {

        @Override
        public AtualizarHabitoRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws JacksonException {
            JsonNode node = p.readValueAsTree();
            boolean horarioPreferencialPresente = node.has("horarioPreferencial");
            LocalTime horarioPreferencial = null;
            if (horarioPreferencialPresente && !node.get("horarioPreferencial").isNull()) {
                horarioPreferencial = LocalTime.parse(node.get("horarioPreferencial").asString());
            }
            boolean tagIdsPresente = node.has("tagIds");
            List<UUID> tagIds = null;
            if (tagIdsPresente) {
                tagIds = uuids(node.get("tagIds"));
            }
            return new AtualizarHabitoRequest(
                    texto(node, "titulo"),
                    texto(node, "descricao"),
                    inteiroOuNulo(node, "duracaoMinutos"),
                    diasSemanaOuNulo(node),
                    horarioPreferencial,
                    horarioPreferencialPresente,
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

        private static Integer inteiroOuNulo(JsonNode node, String campo) {
            JsonNode valor = node.get(campo);
            if (valor == null || valor.isNull()) {
                return null;
            }
            return valor.asInt();
        }

        private static Set<DiaSemana> diasSemanaOuNulo(JsonNode node) {
            JsonNode valor = node.get("diasSemana");
            if (valor == null || valor.isNull()) {
                return null;
            }
            Set<DiaSemana> dias = new HashSet<>();
            for (JsonNode item : valor) {
                dias.add(DiaSemana.valueOf(item.asString()));
            }
            return dias;
        }
    }
}
