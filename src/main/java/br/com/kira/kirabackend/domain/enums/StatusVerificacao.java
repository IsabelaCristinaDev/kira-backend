package br.com.kira.kirabackend.domain.enums;

/**
 * Situação da verificação de identidade. Os nomes ficam em inglês porque o app lê estes valores
 * (contrato combinado com a Wanessa).
 */
public enum StatusVerificacao {
    /** Criada e ainda sem resposta do serviço (por exemplo, quando ele estava fora do ar). */
    PENDING,
    /** O serviço aprovou: o perfil passa a ser verificado. */
    VERIFIED,
    /** O serviço ficou em dúvida: aguarda revisão manual de um ADMIN. */
    IN_REVIEW,
    /** O serviço reprovou: a pessoa pode tentar de novo, se ainda tiver tentativas. */
    REJECTED
}
