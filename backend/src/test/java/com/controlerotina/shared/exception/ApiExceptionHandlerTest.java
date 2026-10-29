package com.controlerotina.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void deveMapearJsonInvalidoPara400() {
        HttpInputMessage input = new MockHttpInputMessage("{".getBytes());
        ProblemDetail problem = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("JSON parse error", input));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Requisição inválida");
        assertThat(problem.getDetail()).isEqualTo("O corpo da requisição está malformado ou ilegível.");
        assertThat(problem.getDetail()).doesNotContain("JSON parse error");
    }

    @Test
    void deveMapearTipoDeParametroInvalidoPara400() {
        ProblemDetail problem = handler.handleMethodArgumentTypeMismatch(
                new MethodArgumentTypeMismatchException(
                        "nao-e-uuid",
                        UUID.class,
                        "id",
                        null,
                        new IllegalArgumentException("Invalid UUID")));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Parâmetro inválido");
        assertThat(problem.getDetail()).isEqualTo("O valor informado não é compatível com o tipo esperado.");
        assertThat(problem.getDetail()).doesNotContain("Invalid UUID");
    }

    @Test
    void deveMapearParametroObrigatorioAusentePara400() {
        ProblemDetail problem = handler.handleMissingServletRequestParameter(
                new MissingServletRequestParameterException("data", "LocalDate"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Parâmetro obrigatório ausente");
        assertThat(problem.getDetail()).isEqualTo("Um parâmetro obrigatório não foi informado.");
    }

    @Test
    void deveMapearUkUsuarioEmailPara409() {
        ProblemDetail problem = handler.handleDataIntegrity(
                new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint \"uk_usuario_email\""));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Email já cadastrado");
        assertThat(problem.getDetail()).isNotBlank();
    }

    @Test
    void deveMapearUkPlanoUsuarioDataVersaoPara409() {
        ProblemDetail problem = handler.handleDataIntegrity(
                new DataIntegrityViolationException("violates unique constraint \"uk_plano_usuario_data_versao\""));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Versão de plano já existe");
    }

    @Test
    void deveMapearUkExecucaoBlocoPlanoPara409() {
        ProblemDetail problem = handler.handleDataIntegrity(
                new DataIntegrityViolationException("uk_execucao_bloco_plano"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Execução já existe para o bloco");
    }

    @Test
    void deveMapearUkXpEventoTipoOrigemPara409() {
        ProblemDetail problem = handler.handleDataIntegrity(
                new DataIntegrityViolationException("uk_xp_evento_tipo_origem"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Evento de XP já registrado");
    }

    @Test
    void deveMapearUniquesDeCapacidadePara409() {
        assertThat(handler.handleDataIntegrity(
                new DataIntegrityViolationException("uq_capacidade_semanal_usuario_dia_ativo")).getTitle())
                .isEqualTo("Capacidade semanal já existe");
        assertThat(handler.handleDataIntegrity(
                new DataIntegrityViolationException("uq_capacidade_override_usuario_data_ativo")).getTitle())
                .isEqualTo("Capacidade override já existe");
    }
}
