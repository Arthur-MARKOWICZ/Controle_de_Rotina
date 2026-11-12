package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CicloEsperaException extends ErrorResponseException {

    public CicloEsperaException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Espera inválida");
        getBody().setDetail("Esta espera fecharia um ciclo.");
    }
}
