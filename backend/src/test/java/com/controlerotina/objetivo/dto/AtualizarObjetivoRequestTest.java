package com.controlerotina.objetivo.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

class AtualizarObjetivoRequestTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void prazoAusenteNaoMarcaPrazoPresente() {
        AtualizarObjetivoRequest request = ler("{\"titulo\":\"  Novo  \"}");

        assertThat(request.titulo()).isEqualTo("Novo");
        assertThat(request.prazoPresente()).isFalse();
        assertThat(request.prazo()).isNull();
    }

    @Test
    void prazoNuloMarcaPresenteSemValor() {
        AtualizarObjetivoRequest request = ler("{\"prazo\":null}");

        assertThat(request.prazoPresente()).isTrue();
        assertThat(request.prazo()).isNull();
        assertThat(request.titulo()).isNull();
    }

    @Test
    void prazoValidoEhConvertidoParaData() {
        AtualizarObjetivoRequest request = ler("{\"prazo\":\"2026-12-31\"}");

        assertThat(request.prazoPresente()).isTrue();
        assertThat(request.prazo()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void descricaoEmBrancoChegaVaziaParaLimpar() {
        AtualizarObjetivoRequest request = ler("{\"descricao\":\"   \"}");

        assertThat(request.descricao()).isEmpty();
    }

    @Test
    void prazoMalformadoLancaExcecaoDoJackson() {
        assertThatThrownBy(() -> ler("{\"prazo\":\"31/12/2026\"}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void prazoComMesInexistenteLancaExcecaoDoJackson() {
        assertThatThrownBy(() -> ler("{\"prazo\":\"2026-13-01\"}"))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void prazoBooleanoLancaExcecaoDoJackson() {
        assertThatThrownBy(() -> ler("{\"prazo\":true}"))
                .isInstanceOf(JacksonException.class);
    }

    private static AtualizarObjetivoRequest ler(String json) {
        return JSON.readValue(json, AtualizarObjetivoRequest.class);
    }
}
