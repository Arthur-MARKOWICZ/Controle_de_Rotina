package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class SubtarefaObjetivoException extends ErrorResponseException {

    public SubtarefaObjetivoException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Subtarefa não pode ser vinculada a objetivo");
        getBody().setDetail("Só é possível vincular objetivos a uma tarefa raiz.");
    }
}
