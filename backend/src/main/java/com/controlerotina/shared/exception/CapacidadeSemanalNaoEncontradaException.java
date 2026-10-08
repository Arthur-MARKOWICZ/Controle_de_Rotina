package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CapacidadeSemanalNaoEncontradaException extends ErrorResponseException {

    public CapacidadeSemanalNaoEncontradaException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Capacidade semanal não encontrada");
        getBody().setDetail("Capacidade inexistente ou sem permissão de acesso.");
    }
}
