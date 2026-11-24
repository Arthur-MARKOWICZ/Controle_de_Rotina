package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class PlanoVazioException extends ErrorResponseException {

    public PlanoVazioException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Plano vazio");
        getBody().setDetail(
                "Não há blocos para aprovar. Ajuste a capacidade ou adicione tarefas e gere uma nova versão.");
    }
}
