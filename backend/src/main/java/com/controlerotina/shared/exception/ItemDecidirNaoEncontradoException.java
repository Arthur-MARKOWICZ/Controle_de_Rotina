package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ItemDecidirNaoEncontradoException extends ErrorResponseException {

    public ItemDecidirNaoEncontradoException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Item Decidir não encontrado");
        getBody().setDetail("Item inexistente ou sem permissão de acesso.");
    }
}
