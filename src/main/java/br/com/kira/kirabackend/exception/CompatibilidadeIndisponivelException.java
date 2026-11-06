package br.com.kira.kirabackend.exception;

/**
 * A IA de compatibilidade (kira-ai) está fora do ar, demorou ou respondeu fora do contrato.
 * O app mantém a ordem que já calcula sozinho; por isso não há tentativa de novo aqui.
 */
public class CompatibilidadeIndisponivelException extends RuntimeException {
    public CompatibilidadeIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
