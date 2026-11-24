package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ObjetivoJaAtivoException extends ErrorResponseException {

    public ObjetivoJaAtivoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Objetivo já ativo");
        getBody().setDetail("O objetivo informado já está ativo.");
    }
}
