package com.controlerotina.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ItemDecidirJaPromovidoException extends ErrorResponseException {

    public ItemDecidirJaPromovidoException() {
        super(HttpStatus.CONFLICT);
        getBody().setTitle("Item Decidir já promovido");
        getBody().setDetail("Item promovido é somente leitura e não pode ser alterado nem promovido de novo.");
    }
}
