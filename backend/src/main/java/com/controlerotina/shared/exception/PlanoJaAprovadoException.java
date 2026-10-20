package com.controlerotina.shared.exception;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PlanoJaAprovadoException extends ErrorResponseException {

    public PlanoJaAprovadoException(LocalDate data) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Plano já aprovado");
        getBody().setDetail("Já existe um plano aprovado para a data informada.");
        getBody().setProperty("data", data.toString());
    }
}
