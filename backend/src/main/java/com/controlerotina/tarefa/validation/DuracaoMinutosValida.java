package com.controlerotina.tarefa.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = DuracaoMinutosValidaValidator.class)
@Target({FIELD, PARAMETER, METHOD})
@Retention(RUNTIME)
public @interface DuracaoMinutosValida {

    String message() default "duração deve ser no mínimo 15 minutos e múltiplo de 15";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
