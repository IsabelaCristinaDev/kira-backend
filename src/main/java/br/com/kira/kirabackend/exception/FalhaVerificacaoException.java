package br.com.kira.kirabackend.exception;

/**
 * Erro na conversa com o serviço de verificação que tentar de novo não resolve, como chave de serviço
 * errada (401) ou resposta fora do contrato. O detalhe fica só no log, nunca a chave nem dados pessoais.
 */
public class FalhaVerificacaoException extends RuntimeException {
    public FalhaVerificacaoException(String message) {
        super(message);
    }

    public FalhaVerificacaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
