package com.controlerotina.usuario.validation;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SenhaForteValidator implements ConstraintValidator<SenhaForte, String> {

    /**
     * Critérios compartilhados com o front:
     * 8–72 caracteres; ao menos 1 minúscula, 1 maiúscula, 1 dígito e 1 especial.
     */
    private static final Pattern SENHA_FORTE = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.isBlank()) {
            return false;
        }
        return SENHA_FORTE.matcher(value).matches();
    }
}
