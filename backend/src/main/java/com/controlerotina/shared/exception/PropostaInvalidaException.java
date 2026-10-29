package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PropostaInvalidaException extends ErrorResponseException {

    public PropostaInvalidaException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Proposta inválida");
        getBody().setDetail("A proposta não existe, já foi resolvida ou não pertence ao usuário.");
    }
}
