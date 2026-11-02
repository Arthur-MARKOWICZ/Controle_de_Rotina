package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HabitoNaoEncontradoException extends ErrorResponseException {

    public HabitoNaoEncontradoException() {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Hábito não encontrado");
        getBody().setDetail("Hábito inexistente ou sem permissão de acesso.");
    }
}
