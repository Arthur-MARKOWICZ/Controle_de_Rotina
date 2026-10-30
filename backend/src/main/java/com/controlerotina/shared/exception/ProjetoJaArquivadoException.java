package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ProjetoJaArquivadoException extends ErrorResponseException {

    public ProjetoJaArquivadoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Projeto já arquivado");
        getBody().setDetail("O projeto informado já está arquivado.");
    }
}
