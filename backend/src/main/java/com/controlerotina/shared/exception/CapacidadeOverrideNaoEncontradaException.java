package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CapacidadeOverrideNaoEncontradaException extends ErrorResponseException {

    public CapacidadeOverrideNaoEncontradaException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Capacidade override não encontrada");
        getBody().setDetail("Capacidade inexistente ou sem permissão de acesso.");
    }
}
