package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CapacidadeSemanalConflitoException extends ErrorResponseException {

    public CapacidadeSemanalConflitoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Capacidade semanal já existe");
        getBody().setDetail("Já existe capacidade ativa para o dia da semana informado.");
    }

    public CapacidadeSemanalConflitoException(String diaSemana) {
        this();
        getBody().setProperty("diaSemana", diaSemana);
    }
}
