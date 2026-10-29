package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class OrdenacaoInvalidaException extends ErrorResponseException {

    public OrdenacaoInvalidaException(String propriedade) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Ordenação inválida");
        getBody().setDetail("Ordenação inválida: " + propriedade);
        getBody().setProperty("propriedade", propriedade);
    }
}
