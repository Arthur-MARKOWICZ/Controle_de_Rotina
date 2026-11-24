package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CombinacaoStatusInvalidaException extends ErrorResponseException {

    public CombinacaoStatusInvalidaException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Combinação de status inválida");
        getBody().setDetail("Status ATIVO não pode ser combinado com CONCLUIDO ou ARQUIVADO.");
    }
}
