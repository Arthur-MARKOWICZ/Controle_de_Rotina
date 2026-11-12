package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class EsperaNaoEncontradaException extends ErrorResponseException {

    public EsperaNaoEncontradaException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Espera não encontrada");
        getBody().setDetail("A espera informada não foi encontrada.");
    }
}
