package br.com.kira.kirabackend.domain.enums;

/** Mudanças feitas pelo estúdio que a cliente precisa saber. O código é o "tipo" que o app recebe no push. */
public enum TipoAvisoAgendamento {
    CONFIRMADO("AGENDAMENTO_CONFIRMADO"),
    CANCELADO_PELO_ESTUDIO("AGENDAMENTO_CANCELADO"),
    REMARCADO_PELO_ESTUDIO("AGENDAMENTO_REMARCADO");

    private final String codigo;

    TipoAvisoAgendamento(String codigo) {
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
