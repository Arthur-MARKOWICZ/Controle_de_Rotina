package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ObjetivoNaoAtivoException extends ErrorResponseException {

    public ObjetivoNaoAtivoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Objetivo não está ativo");
        getBody().setDetail("Só é possível vincular a um objetivo ativo.");
    }
}
