package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DataDuplicadaNoLoteException extends ErrorResponseException {

    public DataDuplicadaNoLoteException(String data) {
        super(HttpStatus.BAD_REQUEST);
        getBody().setTitle("Data duplicada no lote");
        getBody().setDetail("Data duplicada no lote: " + data);
        getBody().setProperty("data", data);
    }
}
