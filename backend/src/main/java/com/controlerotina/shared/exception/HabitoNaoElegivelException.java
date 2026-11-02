package com.controlerotina.shared.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class HabitoNaoElegivelException extends ErrorResponseException {

    public HabitoNaoElegivelException(UUID habitoId) {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Hábito não elegível");
        getBody().setDetail("O hábito não pode ser adicionado ao plano (status, exclusão ou dia da semana).");
        getBody().setProperty("habitoId", habitoId.toString());
    }
}
