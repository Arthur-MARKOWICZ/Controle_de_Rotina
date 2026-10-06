package com.controlerotina.usuario.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = SenhaForteValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface SenhaForte {

    String message() default "senha deve ter entre 8 e 72 caracteres, com letra maiúscula, minúscula, número e caractere especial";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
