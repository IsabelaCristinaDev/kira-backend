package br.com.kira.kirabackend.exception;

/** A conta logada não é dona do recurso da rota (ex.: dados de outro estabelecimento ou de outra cliente). */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
