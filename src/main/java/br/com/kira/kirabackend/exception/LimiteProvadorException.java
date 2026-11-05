package br.com.kira.kirabackend.exception;

/** A pessoa já gerou todas as prévias do Provador permitidas no dia. */
public class LimiteProvadorException extends RuntimeException {
    public LimiteProvadorException(int limiteDiario) {
        super("Você chegou ao limite de " + limiteDiario + " prévias por dia. Tente novamente amanhã");
    }
}
