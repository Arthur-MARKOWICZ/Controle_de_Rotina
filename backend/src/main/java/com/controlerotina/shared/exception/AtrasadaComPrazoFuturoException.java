package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class AtrasadaComPrazoFuturoException extends ErrorResponseException {

    public AtrasadaComPrazoFuturoException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Status inválido");
        getBody().setDetail("Não é possível marcar como ATRASADA quando o prazo ainda está no futuro.");
    }
}
