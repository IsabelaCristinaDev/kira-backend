package br.com.kira.kirabackend.domain.enums;

public enum StatusAgendamento {
    PENDENTE,
    CONFIRMADO,
    /** A cliente chegou ao estúdio: o estúdio validou o código de chegada que ela mostrou. */
    CHEGADA_CONFIRMADA,
    CANCELADO,
    CONCLUIDO,
    REAGENDADO
}