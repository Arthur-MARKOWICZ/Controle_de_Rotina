package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DuracaoSessaoInvalidaException extends ErrorResponseException {

    public DuracaoSessaoInvalidaException(int restante) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Duração da sessão inválida");
        getBody().setDetail(
                "Informe duracaoMinutos entre 1 e o restante da tarefa (" + restante + ")."
        );
        getBody().setProperty("restanteMinutos", restante);
    }
}
