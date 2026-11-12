package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ReordenacaoInvalidaException extends ErrorResponseException {

    public ReordenacaoInvalidaException() {
        this("Não foi possível reordenar os itens.");
    }

    public ReordenacaoInvalidaException(String detalhe) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Reordenação inválida");
        getBody().setDetail(detalhe);
    }
}
