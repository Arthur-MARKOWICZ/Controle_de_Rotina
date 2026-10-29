package com.controlerotina.capacidade.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CriarCapacidadeSemanalRequest.Deserializer.class)
public record CriarCapacidadeSemanalRequest(
        @NotEmpty(message = "Informe ao menos um dia da semana.")
        List<@Valid CriarCapacidadeSemanalItemRequest> itens
) {

    public static class Deserializer extends ValueDeserializer<CriarCapacidadeSemanalRequest> {

        @Override
        public CriarCapacidadeSemanalRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws JacksonException {
            return new CriarCapacidadeSemanalRequest(
                    ItemOuLista.ler(p, ctxt, CriarCapacidadeSemanalItemRequest.class));
        }
    }
}
