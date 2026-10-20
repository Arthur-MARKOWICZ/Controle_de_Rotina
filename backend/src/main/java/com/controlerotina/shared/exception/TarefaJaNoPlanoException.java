package com.controlerotina.shared.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TarefaJaNoPlanoException extends ErrorResponseException {

    public TarefaJaNoPlanoException(UUID tarefaId) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Tarefa já no plano");
        getBody().setDetail("A tarefa já possui um bloco nesta versão do plano.");
        getBody().setProperty("tarefaId", tarefaId.toString());
    }
}
