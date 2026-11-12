package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class AutoEsperaException extends ErrorResponseException {

    public AutoEsperaException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Espera inválida");
        getBody().setDetail("Origem e alvo não podem ser o mesmo recurso.");
    }
}
