package br.com.kira.kirabackend.exception;

/**
 * O serviço de verificação não respondeu a tempo ou está fora do ar (timeout, falha de conexão ou 503).
 * A verificação continua pendente e o app pode tentar de novo.
 */
public class VerificacaoIndisponivelException extends RuntimeException {
    public VerificacaoIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
