package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CapacidadeOverrideConflitoException extends ErrorResponseException {

    public CapacidadeOverrideConflitoException(String data) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Capacidade override já existe");
        getBody().setDetail("Já existe capacidade ativa para a data informada.");
        getBody().setProperty("data", data);
    }
}
