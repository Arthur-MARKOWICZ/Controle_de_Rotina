package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CapacidadeOverrideConflitoException extends ErrorResponseException {

    public CapacidadeOverrideConflitoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Capacidade override já existe");
        getBody().setDetail("Já existe capacidade ativa para a data informada.");
    }

    public CapacidadeOverrideConflitoException(String data) {
        this();
        getBody().setProperty("data", data);
    }
}
