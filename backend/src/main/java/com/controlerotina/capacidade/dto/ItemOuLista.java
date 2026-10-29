package com.controlerotina.capacidade.dto;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;

/**
 * Aceita JSON de item único ({...}) ou lista ([{...}, ...]) e normaliza para List.
 */
final class ItemOuLista {

    private ItemOuLista() {
    }

    static <T> List<T> ler(JsonParser p, DeserializationContext ctxt, Class<T> itemType) throws JacksonException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.START_ARRAY) {
            JavaType listType = ctxt.getTypeFactory().constructCollectionType(List.class, itemType);
            List<T> itens = ctxt.readValue(p, listType);
            return itens == null ? List.of() : List.copyOf(itens);
        }
        if (token == JsonToken.START_OBJECT) {
            T item = ctxt.readValue(p, itemType);
            List<T> itens = new ArrayList<>(1);
            itens.add(item);
            return List.copyOf(itens);
        }
        if (token == JsonToken.VALUE_NULL) {
            return List.of();
        }
        ctxt.handleUnexpectedToken(List.class, p);
        return List.of();
    }
}
