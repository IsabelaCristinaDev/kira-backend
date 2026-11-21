package br.com.kira.kirabackend.exception;

import lombok.Getter;

/** O código de chegada digitado pelo estúdio não serve. Vira 400 com o motivo no campo "erro". */
@Getter
public class CodigoChegadaException extends RuntimeException {

    public enum Motivo {
        TOKEN_INVALIDO("Código incorreto. Confira com a cliente e tente novamente"),
        TOKEN_EXPIRADO("Este código expirou. Peça à cliente um novo código"),
        TOKEN_JA_UTILIZADO("Este código já foi utilizado");

        private final String mensagem;

        Motivo(String mensagem) {
            this.mensagem = mensagem;
        }
    }

    private final Motivo motivo;

    public CodigoChegadaException(Motivo motivo) {
        super(motivo.mensagem);
        this.motivo = motivo;
    }
}
