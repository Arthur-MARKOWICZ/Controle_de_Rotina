package com.controlerotina.shared.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ErrorResponseException.class)
    public ProblemDetail handleErrorResponse(ErrorResponseException ex) {
        return ex.getBody();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Dados inválidos");
        problem.setDetail("Um ou mais campos estão inválidos.");
        problem.setProperty("errors", errosDeBinding(ex));
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Dados inválidos");
        problem.setDetail("Um ou mais campos estão inválidos.");
        problem.setProperty("errors", errosDeConstraint(ex));
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Requisição inválida");
        problem.setDetail("O corpo da requisição está malformado ou ilegível.");
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Parâmetro inválido");
        problem.setDetail("O valor informado não é compatível com o tipo esperado.");
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Parâmetro obrigatório ausente");
        problem.setDetail("Um parâmetro obrigatório não foi informado.");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        ErrorResponseException mapped = mapearUnique(ex);
        if (mapped != null) {
            return mapped.getBody();
        }
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Erro de integridade");
        problem.setDetail("Não foi possível concluir a operação.");
        return problem;
    }

    private static List<Map<String, String>> errosDeBinding(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("property", fieldError.getField());
            item.put("message", fieldError.getDefaultMessage() == null ? "inválido" : fieldError.getDefaultMessage());
            errors.add(item);
        }
        ex.getBindingResult().getGlobalErrors().forEach(global -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("property", global.getObjectName());
            item.put("message", global.getDefaultMessage() == null ? "inválido" : global.getDefaultMessage());
            errors.add(item);
        });
        return errors;
    }

    private static List<Map<String, String>> errosDeConstraint(ConstraintViolationException ex) {
        List<Map<String, String>> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("property", violation.getPropertyPath().toString());
            item.put("message", violation.getMessage());
            errors.add(item);
        }
        return errors;
    }

    private static ErrorResponseException mapearUnique(DataIntegrityViolationException ex) {
        String mensagem = mensagemCompleta(ex);
        if (contem(mensagem, "uk_usuario_email")) {
            return new EmailJaCadastradoException("");
        }
        if (contem(mensagem, "uk_plano_usuario_data_versao")) {
            return new PlanoVersaoConflitoException();
        }
        if (contem(mensagem, "uk_execucao_bloco_plano")) {
            return new ExecucaoBlocoConflitoException();
        }
        if (contem(mensagem, "uk_xp_evento_tipo_origem")) {
            return new XpEventoDuplicadoException();
        }
        if (contem(mensagem, "uq_capacidade_semanal_usuario_dia_ativo")) {
            return new CapacidadeSemanalConflitoException();
        }
        if (contem(mensagem, "uq_capacidade_override_usuario_data_ativo")) {
            return new CapacidadeOverrideConflitoException();
        }
        if (contem(mensagem, "uk_bloco_plano_plano_tarefa")) {
            return new TarefaJaNoPlanoException();
        }
        return null;
    }

    private static boolean contem(String mensagem, String constraint) {
        return mensagem != null && mensagem.toLowerCase().contains(constraint.toLowerCase());
    }

    private static String mensagemCompleta(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable atual = ex;
        while (atual != null) {
            if (atual.getMessage() != null) {
                sb.append(' ').append(atual.getMessage());
            }
            atual = atual.getCause();
        }
        return sb.toString();
    }
}
