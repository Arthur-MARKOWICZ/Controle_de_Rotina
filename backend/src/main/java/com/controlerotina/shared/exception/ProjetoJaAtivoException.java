package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ProjetoJaAtivoException extends ErrorResponseException {

    public ProjetoJaAtivoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Projeto já ativo");
        getBody().setDetail("O projeto informado já está ativo.");
    }
}
