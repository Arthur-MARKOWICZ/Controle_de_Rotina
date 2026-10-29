package com.controlerotina.tarefa.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DuracaoMinutosValidaValidator implements ConstraintValidator<DuracaoMinutosValida, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value >= 15 && value % 15 == 0;
    }
}
