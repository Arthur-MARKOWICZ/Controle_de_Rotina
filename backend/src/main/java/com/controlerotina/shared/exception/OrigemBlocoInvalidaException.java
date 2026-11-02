package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class OrigemBlocoInvalidaException extends ErrorResponseException {

    public OrigemBlocoInvalidaException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Origem do bloco inválida");
        getBody().setDetail("Informe exatamente um de tarefaId ou habitoId.");
    }
}
