package br.com.kira.kirabackend.exception;

/** O Provador só roda com o consentimento LGPD ativo na versão atual do termo. */
public class ConsentimentoProvadorNecessarioException extends RuntimeException {
    public ConsentimentoProvadorNecessarioException() {
        super("Consentimento do Provador virtual necessário");
    }
}
