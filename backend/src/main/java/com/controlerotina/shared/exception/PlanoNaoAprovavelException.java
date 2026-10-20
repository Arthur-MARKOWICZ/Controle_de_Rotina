package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PlanoNaoAprovavelException extends ErrorResponseException {

    public PlanoNaoAprovavelException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Plano não aprovável");
        getBody().setDetail("Só é possível aprovar a versão corrente em rascunho.");
    }
}
