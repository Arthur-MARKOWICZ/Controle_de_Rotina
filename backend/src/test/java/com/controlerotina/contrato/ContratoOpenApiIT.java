package com.controlerotina.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Garante que {@code contract/openapi.json} é exatamente o spec que o backend gera.
 * Para regenerar: {@value #COMANDO_REGENERAR}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Testcontainers
class ContratoOpenApiIT {

    static final String COMANDO_REGENERAR =
            "cd backend && mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true";

    private static final Path ARQUIVO = Path.of(System.getProperty("contrato.arquivo", "../contract/openapi.json"));
    private static final JsonMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    TestRestTemplate rest;

    @Test
    void specVersionadoDeveSerIgualAoGeradoPeloBackend() throws IOException {
        String gerado = specNormalizado();

        if (Boolean.getBoolean("contrato.atualizar")) {
            Files.createDirectories(ARQUIVO.toAbsolutePath().getParent());
            Files.writeString(ARQUIVO, gerado, StandardCharsets.UTF_8);
            return;
        }
        if (!Files.exists(ARQUIVO)) {
            fail("Contrato não encontrado em " + ARQUIVO.toAbsolutePath() + ". Gere com: " + COMANDO_REGENERAR);
        }
        comparar(gerado, Files.readString(ARQUIVO, StandardCharsets.UTF_8));
    }

    @Test
    void specGeradoDeveSerDeterministico() {
        assertThat(specNormalizado()).isEqualTo(specNormalizado());
    }

    @Test
    void specNaoDeveConterServersComPortaAleatoria() {
        JsonNode spec = MAPPER.readTree(specNormalizado());

        assertThat(spec.has("servers")).isFalse();
    }

    @Test
    void deveFalharComComandoDeRegeneracaoQuandoArquivoDiverge() {
        String gerado = specNormalizado();
        String adulterado = gerado.replaceFirst("\"titulo\"", "\"tituloAntigo\"");

        assertThatThrownBy(() -> comparar(gerado, adulterado))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(COMANDO_REGENERAR);
    }

    @Test
    void campoNaoAnulavelDeRespostaDeveSerObrigatorioENaoAceitarNull() {
        JsonNode tarefa = schema("TarefaResponse");

        assertThat(lista(tarefa.path("required"))).contains("id", "titulo", "prazo");
        assertThat(aceitaNull(tarefa.path("properties").path("id"))).isFalse();
    }

    @Test
    void campoAnulavelDeRespostaDeveSerObrigatorioEAceitarNull() {
        JsonNode prazo = schema("TarefaResponse").path("properties").path("prazo");

        assertThat(aceitaNull(prazo)).isTrue();
    }

    @Test
    void requisicaoDeveTerObrigatorioSoOQueAValidacaoExige() {
        assertThat(lista(schema("CriarTagRequest").path("required"))).containsExactly("nome");
        assertThat(schema("AtualizarTagRequest").has("required")).isFalse();
    }

    static void comparar(String gerado, String versionado) {
        if (!gerado.equals(versionado)) {
            throw new AssertionError("contract/openapi.json está desatualizado em relação ao backend. "
                    + "Regenere com: " + COMANDO_REGENERAR
                    + " e depois os tipos (frontend: npm run gerar:contrato; mcp: pnpm gerar:contrato).");
        }
    }

    private String specNormalizado() {
        String token = ContratoTestSupport.tokenNovoUsuario(rest);
        String bruto = ContratoTestSupport.get(rest, "/v3/api-docs", token).getBody();
        ObjectNode spec = (ObjectNode) MAPPER.readTree(bruto);
        spec.remove("servers");
        return MAPPER.writeValueAsString(normalizar(spec)) + "\n";
    }

    private JsonNode schema(String nome) {
        return MAPPER.readTree(specNormalizado()).path("components").path("schemas").path(nome);
    }

    /** Ordena chaves de objetos e os arrays {@code required}/{@code enum}/{@code type}, para diff estável. */
    private static JsonNode normalizar(JsonNode node) {
        if (node instanceof ObjectNode objeto) {
            Map<String, JsonNode> ordenado = new TreeMap<>();
            objeto.properties().forEach(e -> ordenado.put(e.getKey(), normalizarCampo(e.getKey(), e.getValue())));
            ObjectNode novo = MAPPER.createObjectNode();
            ordenado.forEach(novo::set);
            return novo;
        }
        if (node instanceof ArrayNode array) {
            ArrayNode novo = MAPPER.createArrayNode();
            array.forEach(item -> novo.add(normalizar(item)));
            return novo;
        }
        return node;
    }

    private static JsonNode normalizarCampo(String chave, JsonNode valor) {
        if (valor instanceof ArrayNode array && List.of("required", "enum", "type").contains(chave)) {
            List<String> itens = new ArrayList<>(lista(array));
            itens.sort(null);
            ArrayNode novo = MAPPER.createArrayNode();
            itens.forEach(novo::add);
            return novo;
        }
        return normalizar(valor);
    }

    private static List<String> lista(JsonNode array) {
        List<String> itens = new ArrayList<>();
        array.forEach(item -> itens.add(item.asString()));
        return itens;
    }

    private static boolean aceitaNull(JsonNode propriedade) {
        if (lista(propriedade.path("type")).contains("null")) {
            return true;
        }
        for (String composicao : List.of("oneOf", "anyOf")) {
            for (JsonNode alternativa : propriedade.path(composicao)) {
                if ("null".equals(alternativa.path("type").asString())) {
                    return true;
                }
            }
        }
        return false;
    }
}
