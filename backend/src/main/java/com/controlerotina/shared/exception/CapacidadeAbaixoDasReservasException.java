package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CapacidadeAbaixoDasReservasException extends ErrorResponseException {

    public CapacidadeAbaixoDasReservasException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Capacidade abaixo das reservas");
        getBody().setDetail(
                "Reduza as reservas de projeto deste dia antes de diminuir as horas do template."
        );
    }
}
