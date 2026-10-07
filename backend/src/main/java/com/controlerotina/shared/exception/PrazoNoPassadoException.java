package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PrazoNoPassadoException extends ErrorResponseException {

    public PrazoNoPassadoException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Prazo inválido");
        getBody().setDetail("O prazo não pode estar no passado.");
    }
}
