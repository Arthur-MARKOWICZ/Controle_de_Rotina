package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ObjetivoNaoEncontradoException extends ErrorResponseException {

    public ObjetivoNaoEncontradoException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Objetivo não encontrado");
        getBody().setDetail("Objetivo inexistente ou sem permissão de acesso.");
    }
}
