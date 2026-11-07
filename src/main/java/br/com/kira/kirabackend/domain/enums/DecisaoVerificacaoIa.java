package br.com.kira.kirabackend.domain.enums;

/**
 * Decisão devolvida pelo kira-verify-ai no campo status. Os limiares ficam no serviço:
 * o backend só lê a decisão e aplica no usuário.
 */
public enum DecisaoVerificacaoIa {
    APPROVED,
    REVIEW,
    REJECTED
}
