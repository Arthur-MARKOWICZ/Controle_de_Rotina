package com.controlerotina.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * O endpoint do spec depende só de configuração: desligado no {@code application.yml} base (produção)
 * e ligado no perfil {@code local}. Lê os arquivos de {@code src/main/resources} direto, porque o
 * {@code application.yml} de teste sobrepõe o principal no classpath.
 */
class ContratoConfigPerfisTest {

    private static final String CHAVE = "springdoc.api-docs.enabled";

    @Test
    void perfilLocalDeveLigarEndpointDoSpec() throws IOException {
        assertThat(valor("application-local.yml")).isEqualTo(true);
    }

    @Test
    void configuracaoBaseDeProducaoDeveDesligarEndpointDoSpec() throws IOException {
        assertThat(valor("application.yml")).isEqualTo(false);
    }

    private static Object valor(String arquivo) throws IOException {
        List<PropertySource<?>> fontes = new YamlPropertySourceLoader()
                .load(arquivo, new FileSystemResource("src/main/resources/" + arquivo));
        return fontes.stream().map(f -> f.getProperty(CHAVE)).filter(v -> v != null).findFirst().orElse(null);
    }
}
