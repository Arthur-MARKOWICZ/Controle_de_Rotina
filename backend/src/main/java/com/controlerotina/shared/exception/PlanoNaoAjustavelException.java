package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PlanoNaoAjustavelException extends ErrorResponseException {

    public PlanoNaoAjustavelException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Plano não ajustável");
        getBody().setDetail("Só é possível ajustar a versão corrente em rascunho.");
    }
}
