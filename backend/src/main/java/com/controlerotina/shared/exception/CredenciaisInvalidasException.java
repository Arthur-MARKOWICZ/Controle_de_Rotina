package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CredenciaisInvalidasException extends ErrorResponseException {

    public CredenciaisInvalidasException() {
        super(HttpStatus.UNAUTHORIZED);
        getBody().setTitle("Não autorizado");
        getBody().setDetail("Credenciais inválidas.");
    }
}
