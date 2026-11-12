package com.controlerotina.shared.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TarefaBloqueadaException extends ErrorResponseException {

    public TarefaBloqueadaException(UUID tarefaId) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Tarefa bloqueada");
        getBody().setDetail("A tarefa está bloqueada por uma espera ainda não resolvida.");
        getBody().setProperty("tarefaId", tarefaId.toString());
    }
}
