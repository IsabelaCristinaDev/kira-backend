package br.com.kira.kirabackend.exception;

/** A ação exige a identidade verificada e a conta ainda não foi verificada (só com VERIFICACAO_OBRIGATORIA=true). */
public class VerificacaoObrigatoriaException extends RuntimeException {

    public VerificacaoObrigatoriaException(String mensagem) {
        super(mensagem);
    }
}
