package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class SubtarefaNivelInvalidoException extends ErrorResponseException {

    public SubtarefaNivelInvalidoException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Subtarefa inválida");
        getBody().setDetail("Não é possível criar subtarefa de outra subtarefa.");
    }
}
