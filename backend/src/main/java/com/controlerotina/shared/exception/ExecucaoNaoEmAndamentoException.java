package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ExecucaoNaoEmAndamentoException extends ErrorResponseException {

    public ExecucaoNaoEmAndamentoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Execução não está em andamento");
        getBody().setDetail("Só é possível concluir ou abortar uma execução em andamento.");
    }
}
