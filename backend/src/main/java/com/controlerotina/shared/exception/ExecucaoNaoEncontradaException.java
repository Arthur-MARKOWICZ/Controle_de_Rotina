package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ExecucaoNaoEncontradaException extends ErrorResponseException {

    public ExecucaoNaoEncontradaException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Execução não encontrada");
        getBody().setDetail("Execução inexistente ou sem permissão de acesso.");
    }
}
