package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ReservaProjetoDuplicadaException extends ErrorResponseException {

    public ReservaProjetoDuplicadaException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Reserva já existe");
        getBody().setDetail("Já existe reserva deste projeto para o dia da semana informado.");
    }
}
