package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ObjetivoJaArquivadoException extends ErrorResponseException {

    public ObjetivoJaArquivadoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Objetivo já arquivado");
        getBody().setDetail("O objetivo informado já está arquivado.");
    }
}
