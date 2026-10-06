package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class EmailJaCadastradoException extends ErrorResponseException {

    public EmailJaCadastradoException(String email) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Email já cadastrado");
        getBody().setDetail("Já existe uma conta com o email informado.");
        getBody().setProperty("email", email);
    }
}
