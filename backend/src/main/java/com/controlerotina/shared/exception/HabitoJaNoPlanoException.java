package com.controlerotina.shared.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HabitoJaNoPlanoException extends ErrorResponseException {

    public HabitoJaNoPlanoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Hábito já no plano");
        getBody().setDetail("O hábito já possui um bloco nesta versão do plano.");
    }

    public HabitoJaNoPlanoException(UUID habitoId) {
        this();
        getBody().setProperty("habitoId", habitoId.toString());
    }
}
