package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ReservaCapacidadeInsuficienteException extends ErrorResponseException {

    public ReservaCapacidadeInsuficienteException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Capacidade insuficiente");
        getBody().setDetail(
                "A soma das reservas do dia excede a capacidade do template semanal ativo."
        );
    }
}
