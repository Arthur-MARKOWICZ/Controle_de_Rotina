package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ProjetoNaoEncontradoException extends ErrorResponseException {

    public ProjetoNaoEncontradoException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Projeto não encontrado");
        getBody().setDetail("Projeto inexistente ou sem permissão de acesso.");
    }
}
