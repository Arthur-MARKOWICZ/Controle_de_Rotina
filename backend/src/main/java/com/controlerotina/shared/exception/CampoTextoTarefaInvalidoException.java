package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CampoTextoTarefaInvalidoException extends ErrorResponseException {

    public CampoTextoTarefaInvalidoException(String detail) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Campo inválido");
        getBody().setDetail(detail);
    }
}
