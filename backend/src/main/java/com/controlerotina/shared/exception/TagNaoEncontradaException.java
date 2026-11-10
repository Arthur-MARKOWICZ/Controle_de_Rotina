package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TagNaoEncontradaException extends ErrorResponseException {

    public TagNaoEncontradaException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Tag não encontrada");
        getBody().setDetail("Tag inexistente ou sem permissão de acesso.");
    }
}
