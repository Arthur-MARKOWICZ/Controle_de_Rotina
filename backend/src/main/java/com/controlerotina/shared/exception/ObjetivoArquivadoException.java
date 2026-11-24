package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ObjetivoArquivadoException extends ErrorResponseException {

    public ObjetivoArquivadoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Objetivo arquivado");
        getBody().setDetail("Reabra o objetivo antes de concluí-lo.");
    }
}
