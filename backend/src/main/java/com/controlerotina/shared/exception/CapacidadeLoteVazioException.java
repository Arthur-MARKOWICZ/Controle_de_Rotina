package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class CapacidadeLoteVazioException extends ErrorResponseException {

    public CapacidadeLoteVazioException(String detail) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Lote inválido");
        getBody().setDetail(detail);
    }
}
