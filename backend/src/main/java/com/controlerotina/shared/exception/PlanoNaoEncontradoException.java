package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PlanoNaoEncontradoException extends ErrorResponseException {

    public PlanoNaoEncontradoException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Plano não encontrado");
        getBody().setDetail("Plano inexistente ou sem permissão de acesso.");
    }
}
