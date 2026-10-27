package com.controlerotina.historico;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class SemanaInicioInvalidoException extends ErrorResponseException {

    public SemanaInicioInvalidoException(LocalDate semanaInicio) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("semanaInicio inválido");
        getBody().setDetail("semanaInicio deve ser uma segunda-feira (semana seg–dom).");
        getBody().setProperty("semanaInicio", semanaInicio.toString());
    }
}
