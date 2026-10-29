package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class IntervaloCapacidadeInvalidoException extends ErrorResponseException {

    public IntervaloCapacidadeInvalidoException(String detail) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Intervalo inválido");
        getBody().setDetail(detail);
    }
}
