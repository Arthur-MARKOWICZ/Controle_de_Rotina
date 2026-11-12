package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class EsperaDuplicadaException extends ErrorResponseException {

    public EsperaDuplicadaException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Espera já existe");
        getBody().setDetail("Já existe espera deste par origem e alvo.");
    }
}
