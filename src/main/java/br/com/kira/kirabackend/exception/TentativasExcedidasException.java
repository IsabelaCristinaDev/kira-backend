package br.com.kira.kirabackend.exception;

import lombok.Getter;

import java.time.Duration;

/** Tentativas demais em pouco tempo: vira 429 com o header Retry-After no GlobalExceptionHandler. */
@Getter
public class TentativasExcedidasException extends RuntimeException {

    private final Duration espera;

    public TentativasExcedidasException(Duration espera) {
        super(mensagem(espera));
        this.espera = espera;
    }

    /** Segundos inteiros, arredondando para cima: o formato do header Retry-After. */
    public long esperaEmSegundos() {
        long segundos = espera.toSeconds();
        return espera.minusSeconds(segundos).isZero() ? segundos : segundos + 1;
    }

    private static String mensagem(Duration espera) {
        long minutos = Math.max(1, (espera.toSeconds() + 59) / 60);
        return "Muitas tentativas. Tente de novo em " + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }
}
