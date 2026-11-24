package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ObjetivoJaConcluidoException extends ErrorResponseException {

    public ObjetivoJaConcluidoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Objetivo já concluído");
        getBody().setDetail("O objetivo informado já está concluído.");
    }
}
