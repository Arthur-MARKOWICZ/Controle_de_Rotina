package com.controlerotina.tarefa.dto;

import java.time.Instant;
import java.util.UUID;

import com.controlerotina.tarefa.entity.Prioridade;
import com.controlerotina.tarefa.entity.StatusTarefa;
import com.controlerotina.tarefa.validation.DuracaoMinutosValida;

import jakarta.validation.constraints.Size;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = AtualizarTarefaRequest.Deserializer.class)
public record AtualizarTarefaRequest(
        @Size(max = 120)
        String titulo,

        @Size(max = 2000)
        String descricao,

        Prioridade prioridade,

        Instant prazo,

        StatusTarefa status,

        @DuracaoMinutosValida
        Integer duracaoMinutos,

        UUID projetoId,

        boolean projetoIdPresente
) {
    public AtualizarTarefaRequest {
        if (titulo != null) {
            titulo = titulo.trim();
        }
        if (descricao != null) {
            descricao = descricao.trim();
        }
    }

    public static class Deserializer extends ValueDeserializer<AtualizarTarefaRequest> {

        @Override
        public AtualizarTarefaRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws JacksonException {
            JsonNode node = p.readValueAsTree();
            boolean projetoIdPresente = node.has("projetoId");
            UUID projetoId = null;
            if (projetoIdPresente && !node.get("projetoId").isNull()) {
                projetoId = UUID.fromString(node.get("projetoId").asString());
            }
            return new AtualizarTarefaRequest(
                    texto(node, "titulo"),
                    texto(node, "descricao"),
                    enumOuNulo(node, "prioridade", Prioridade.class),
                    instantOuNulo(node, "prazo"),
                    enumOuNulo(node, "status", StatusTarefa.class),
                    inteiroOuNulo(node, "duracaoMinutos"),
                    projetoId,
                    projetoIdPresente
            );
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

        private static Instant instantOuNulo(JsonNode node, String campo) {
            JsonNode valor = node.get(campo);
            if (valor == null || valor.isNull()) {
                return null;
            }
            return Instant.parse(valor.asString());
        }

        private static <E extends Enum<E>> E enumOuNulo(JsonNode node, String campo, Class<E> tipo) {
            JsonNode valor = node.get(campo);
            if (valor == null || valor.isNull()) {
                return null;
            }
            return Enum.valueOf(tipo, valor.asString());
        }
    }
}
