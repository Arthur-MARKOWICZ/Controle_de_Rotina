package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ReservaProjetoNaoEncontradaException extends ErrorResponseException {

    public ReservaProjetoNaoEncontradaException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Reserva não encontrada");
        getBody().setDetail("A reserva informada não foi encontrada.");
    }
}
