package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DiaSemanaDuplicadoNoLoteException extends ErrorResponseException {

    public DiaSemanaDuplicadoNoLoteException(String diaSemana) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Dia duplicado no lote");
        getBody().setDetail("Dia da semana duplicado no lote: " + diaSemana);
        getBody().setProperty("diaSemana", diaSemana);
    }
}
