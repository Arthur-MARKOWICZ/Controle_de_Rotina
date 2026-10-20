package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class BlocoPlanoNaoEncontradoException extends ErrorResponseException {

    public BlocoPlanoNaoEncontradoException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Bloco não encontrado");
        getBody().setDetail("Bloco inexistente neste plano ou sem permissão de acesso.");
    }
}
