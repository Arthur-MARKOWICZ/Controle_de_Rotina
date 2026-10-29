package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PlanoVersaoConflitoException extends ErrorResponseException {

    public PlanoVersaoConflitoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Versão de plano já existe");
        getBody().setDetail("Já existe um plano com a mesma data e versão para este usuário.");
    }
}
