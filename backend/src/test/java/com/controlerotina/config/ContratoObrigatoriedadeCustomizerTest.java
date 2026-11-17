package com.controlerotina.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

class ContratoObrigatoriedadeCustomizerTest {

    private final ContratoObrigatoriedadeCustomizer customizer = new ContratoObrigatoriedadeCustomizer();

    @Test
    void deveMarcarTodasAsPropriedadesDoSchemaDeRespostaComoObrigatorias() {
        Schema<?> tarefa = objeto("titulo", "id", "prazo");
        OpenAPI api = api(Map.of("TarefaResponse", tarefa), operacao(ref("TarefaResponse"), null));

        customizer.customise(api);

        assertThat(tarefa.getRequired()).containsExactly("id", "prazo", "titulo");
    }

    @Test
    void deveSeguirRefAninhadoItemsEComposicao() {
        Schema<?> tag = objeto("id", "nome");
        Schema<?> resumo = objeto("cor");
        Schema<?> pagina = objeto("totalElements");
        Schema<?> tarefa = objeto("id");
        tarefa.addProperty("tags", new ArraySchema().items(ref("TagResumoResponse")));
        tarefa.addProperty("pagina", new Schema<>().allOf(List.of(ref("PageMetadata"))));
        tarefa.addProperty("extra", new ObjectSchema().additionalProperties(ref("Resumo")));
        OpenAPI api = api(
                Map.of("TarefaResponse", tarefa, "TagResumoResponse", tag, "PageMetadata", pagina, "Resumo", resumo),
                operacao(new ArraySchema().items(ref("TarefaResponse")), null));

        customizer.customise(api);

        assertThat(tag.getRequired()).containsExactly("id", "nome");
        assertThat(pagina.getRequired()).containsExactly("totalElements");
        assertThat(resumo.getRequired()).containsExactly("cor");
        assertThat(tarefa.getRequired()).containsExactly("extra", "id", "pagina", "tags");
    }

    @Test
    void naoDeveEntrarEmLoopComRefCiclico() {
        Schema<?> tarefa = objeto("id");
        tarefa.addProperty("pai", ref("TarefaResponse"));
        OpenAPI api = api(Map.of("TarefaResponse", tarefa), operacao(ref("TarefaResponse"), null));

        assertThatCode(() -> customizer.customise(api)).doesNotThrowAnyException();
        assertThat(tarefa.getRequired()).containsExactly("id", "pai");
    }

    @Test
    void naoDeveAlterarSchemaUsadoSoEmRequisicao() {
        Schema<?> patch = objeto("nome", "cor");
        Schema<?> resposta = objeto("id");
        OpenAPI api = api(Map.of("AtualizarTagRequest", patch, "TagResponse", resposta),
                operacao(ref("TagResponse"), ref("AtualizarTagRequest")));

        customizer.customise(api);

        assertThat(patch.getRequired()).isNull();
        assertThat(resposta.getRequired()).containsExactly("id");
    }

    @Test
    void deveManterRequiredDaValidacaoEmRequisicao() {
        Schema<?> criar = objeto("nome", "cor");
        criar.setRequired(List.of("nome"));
        OpenAPI api = api(Map.of("CriarTagRequest", criar), operacao(null, ref("CriarTagRequest")));

        customizer.customise(api);

        assertThat(criar.getRequired()).containsExactly("nome");
    }

    @Test
    void naoDeveFalharComRefParaSchemaInexistente() {
        OpenAPI api = api(Map.of(), operacao(ref("NaoExiste"), null));

        assertThatCode(() -> customizer.customise(api)).doesNotThrowAnyException();
        assertThat(api.getComponents().getSchemas()).isEmpty();
    }

    @Test
    void naoDeveFalharSemPathsSemComponentesNemConteudo() {
        OpenAPI semNada = new OpenAPI();
        OpenAPI semConteudo = api(Map.of("X", objeto("a")), new Operation()
                .responses(new ApiResponses().addApiResponse("204", new ApiResponse())));

        assertThatCode(() -> customizer.customise(semNada)).doesNotThrowAnyException();
        assertThatCode(() -> customizer.customise(semConteudo)).doesNotThrowAnyException();
        assertThat(semConteudo.getComponents().getSchemas().get("X").getRequired()).isNull();
    }

    @Test
    void naoDeveCriarRequiredVazioEmSchemaSemPropriedades() {
        Schema<?> vazio = new ObjectSchema();
        OpenAPI api = api(Map.of("Vazio", vazio), operacao(ref("Vazio"), null));

        customizer.customise(api);

        assertThat(vazio.getRequired()).isNull();
    }

    private static Schema<?> objeto(String... propriedades) {
        Schema<?> schema = new ObjectSchema();
        for (String p : propriedades) {
            schema.addProperty(p, new StringSchema());
        }
        return schema;
    }

    private static Schema<?> ref(String nome) {
        return new Schema<>().$ref("#/components/schemas/" + nome);
    }

    private static Operation operacao(Schema<?> resposta, Schema<?> requisicao) {
        Operation op = new Operation();
        ApiResponse ok = new ApiResponse();
        if (resposta != null) {
            ok.content(new Content().addMediaType("application/json", new MediaType().schema(resposta)));
        }
        op.responses(new ApiResponses().addApiResponse("200", ok));
        if (requisicao != null) {
            op.requestBody(new RequestBody().content(
                    new Content().addMediaType("application/json", new MediaType().schema(requisicao))));
        }
        return op;
    }

    private static OpenAPI api(Map<String, Schema<?>> schemas, Operation op) {
        Map<String, Schema> componentes = new LinkedHashMap<>(schemas);
        return new OpenAPI()
                .components(new Components().schemas(componentes))
                .paths(new Paths().addPathItem("/x", new PathItem().get(op)));
    }
}
