package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HabitoJaPausadoException extends ErrorResponseException {

    public HabitoJaPausadoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Hábito já pausado");
        getBody().setDetail("O hábito informado já está pausado.");
    }
}
