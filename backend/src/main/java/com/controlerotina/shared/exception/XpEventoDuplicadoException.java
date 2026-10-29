package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class XpEventoDuplicadoException extends ErrorResponseException {

    public XpEventoDuplicadoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Evento de XP já registrado");
        getBody().setDetail("Já existe um evento de XP com o mesmo tipo e origem.");
    }
}
