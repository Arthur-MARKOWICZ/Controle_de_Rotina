package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PlanoNaoExecutavelException extends ErrorResponseException {

    public PlanoNaoExecutavelException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Plano não executável");
        getBody().setDetail("Só é possível executar blocos do plano aprovado corrente da data.");
    }
}
