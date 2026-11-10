package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TagNomeDuplicadoException extends ErrorResponseException {

    public TagNomeDuplicadoException(String nome) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Tag já existe");
        getBody().setDetail("Já existe uma tag com este nome.");
        getBody().setProperty("nome", nome);
    }
}
