package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TarefaNaoEncontradaException extends ErrorResponseException {

    public TarefaNaoEncontradaException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Tarefa não encontrada");
        getBody().setDetail("Tarefa inexistente ou sem permissão de acesso.");
    }
}
