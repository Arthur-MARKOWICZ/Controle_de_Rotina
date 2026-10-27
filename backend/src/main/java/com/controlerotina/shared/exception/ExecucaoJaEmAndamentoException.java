package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ExecucaoJaEmAndamentoException extends ErrorResponseException {

    public ExecucaoJaEmAndamentoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Execução já em andamento");
        getBody().setDetail("Este bloco já possui uma execução em andamento.");
    }
}
