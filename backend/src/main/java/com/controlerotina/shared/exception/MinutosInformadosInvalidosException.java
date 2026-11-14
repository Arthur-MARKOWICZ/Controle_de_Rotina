package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class MinutosInformadosInvalidosException extends ErrorResponseException {

    public static final int MINUTOS_MAXIMO = 1440;

    public MinutosInformadosInvalidosException() {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Minutos informados inválidos");
        getBody().setDetail("Informe minutos entre 1 e " + MINUTOS_MAXIMO + ".");
        getBody().setProperty("minutosMaximo", MINUTOS_MAXIMO);
    }
}
