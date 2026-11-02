package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HabitoJaAtivoException extends ErrorResponseException {

    public HabitoJaAtivoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Hábito já ativo");
        getBody().setDetail("O hábito informado já está ativo.");
    }
}
