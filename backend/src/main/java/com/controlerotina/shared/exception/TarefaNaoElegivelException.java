package com.controlerotina.shared.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TarefaNaoElegivelException extends ErrorResponseException {

    public TarefaNaoElegivelException(UUID tarefaId) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Tarefa não elegível");
        getBody().setDetail("A tarefa não pode ser adicionada ao plano (status ou exclusão).");
        getBody().setProperty("tarefaId", tarefaId.toString());
    }
}
