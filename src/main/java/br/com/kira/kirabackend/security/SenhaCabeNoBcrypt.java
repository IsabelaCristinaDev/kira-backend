package br.com.kira.kirabackend.security;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * Senha nova com no máximo 72 bytes em UTF-8, o limite do BCrypt.
 * Conta bytes, não caracteres: uma letra com acento ocupa 2 bytes.
 * Senha ausente é aceita aqui: quem cuida dela é o {@code @NotBlank}.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SenhaCabeNoBcrypt.Validador.class)
public @interface SenhaCabeNoBcrypt {

    int MAXIMO_DE_BYTES = 72;

    String message() default "Senha deve ter no máximo 72 caracteres (letras com acento contam como 2)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SenhaCabeNoBcrypt, String> {
        @Override
        public boolean isValid(String senha, ConstraintValidatorContext contexto) {
            return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_DE_BYTES;
        }
    }
}
