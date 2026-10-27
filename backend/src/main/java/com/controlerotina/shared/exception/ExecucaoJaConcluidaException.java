package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ExecucaoJaConcluidaException extends ErrorResponseException {

    public ExecucaoJaConcluidaException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Execução já concluída");
        getBody().setDetail("Não é possível iniciar um bloco já concluído.");
    }
}
