package com.controlerotina.capacidade.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CriarCapacidadeOverrideRequest.Deserializer.class)
public record CriarCapacidadeOverrideRequest(
        @NotEmpty(message = "Informe ao menos uma data.")
        List<@Valid CriarCapacidadeOverrideItemRequest> itens
) {

    public static class Deserializer extends ValueDeserializer<CriarCapacidadeOverrideRequest> {

        @Override
        public CriarCapacidadeOverrideRequest deserialize(JsonParser p, DeserializationContext ctxt)
                throws JacksonException {
            return new CriarCapacidadeOverrideRequest(
                    ItemOuLista.ler(p, ctxt, CriarCapacidadeOverrideItemRequest.class));
        }
    }
}
