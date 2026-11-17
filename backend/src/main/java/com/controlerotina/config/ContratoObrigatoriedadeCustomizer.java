package com.controlerotina.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

/**
 * Todo schema alcançável a partir de uma resposta passa a ter todas as propriedades em {@code required}:
 * o Jackson serializa todos os campos dos records, inclusive os nulos. A nulidade vem do {@code @Nullable}.
 * Schemas usados só em corpo de requisição ficam como o springdoc gera (required vindo da validação).
 */
@Component
public class ContratoObrigatoriedadeCustomizer implements OpenApiCustomizer {

    private static final String PREFIXO_REF = "#/components/schemas/";

    @Override
    public void customise(OpenAPI openApi) {
        Map<String, Schema> componentes = openApi.getComponents() == null
                ? Map.of()
                : nullSafe(openApi.getComponents().getSchemas());
        Set<String> visitados = new HashSet<>();
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().values().forEach(path -> path.readOperations().stream()
                .map(Operation::getResponses)
                .filter(responses -> responses != null)
                .flatMap(responses -> responses.values().stream())
                .map(ApiResponse::getContent)
                .filter(content -> content != null)
                .flatMap(content -> content.values().stream())
                .forEach(media -> marcar(media.getSchema(), componentes, visitados)));
    }

    private static void marcar(Schema<?> schema, Map<String, Schema> componentes, Set<String> visitados) {
        if (schema == null) {
            return;
        }
        if (schema.get$ref() != null) {
            String nome = schema.get$ref().replace(PREFIXO_REF, "");
            if (visitados.add(nome)) {
                marcar(componentes.get(nome), componentes, visitados);
            }
            return;
        }
        Map<String, Schema> propriedades = nullSafe(schema.getProperties());
        if (!propriedades.isEmpty()) {
            schema.setRequired(new ArrayList<>(new TreeSet<>(propriedades.keySet())));
        }
        propriedades.values().forEach(p -> marcar(p, componentes, visitados));
        marcar(schema.getItems(), componentes, visitados);
        if (schema.getAdditionalProperties() instanceof Schema<?> adicional) {
            marcar(adicional, componentes, visitados);
        }
        for (List<Schema> composicao : List.of(
                nullSafe(schema.getAllOf()), nullSafe(schema.getOneOf()), nullSafe(schema.getAnyOf()))) {
            composicao.forEach(s -> marcar(s, componentes, visitados));
        }
    }

    private static <K, V> Map<K, V> nullSafe(Map<K, V> map) {
        return map == null ? Map.of() : map;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
