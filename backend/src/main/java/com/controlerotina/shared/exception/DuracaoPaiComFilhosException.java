package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DuracaoPaiComFilhosException extends ErrorResponseException {

    public DuracaoPaiComFilhosException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Duração derivada");
        getBody().setDetail("Não é possível alterar a duração de uma tarefa que possui subtarefas.");
    }
}
