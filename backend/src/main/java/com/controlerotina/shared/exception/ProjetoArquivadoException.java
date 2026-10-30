package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ProjetoArquivadoException extends ErrorResponseException {

    public ProjetoArquivadoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Projeto arquivado");
        getBody().setDetail("Não é possível associar a um projeto arquivado.");
    }
}
