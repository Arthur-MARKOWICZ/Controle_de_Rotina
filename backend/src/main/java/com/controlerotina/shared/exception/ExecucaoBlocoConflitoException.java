package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ExecucaoBlocoConflitoException extends ErrorResponseException {

    public ExecucaoBlocoConflitoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Execução já existe para o bloco");
        getBody().setDetail("Já existe uma execução vinculada a este bloco do plano.");
    }
}
