package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PaiComBlocoConcluidoException extends ErrorResponseException {

    public PaiComBlocoConcluidoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Tarefa com progresso");
        getBody().setDetail("Não é possível criar subtarefa em tarefa que já tem bloco concluído.");
    }
}
