package br.com.kira.kirabackend.exception;

/** A geração da prévia falhou. O detalhe fica só no log: a resposta ao app usa uma mensagem fixa. */
public class GeracaoProvadorException extends RuntimeException {
    public GeracaoProvadorException(String message) {
        super(message);
    }

    public GeracaoProvadorException(String message, Throwable cause) {
        super(message, cause);
    }
}
