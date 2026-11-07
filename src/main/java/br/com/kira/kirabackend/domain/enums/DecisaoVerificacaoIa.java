package br.com.kira.kirabackend.domain.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Decisão devolvida pelo kira-verify-ai (POST /verify/identity do kira-ai) no campo status.
 * Os limiares ficam no serviço: o backend só lê a decisão e aplica no usuário.
 */
public enum DecisaoVerificacaoIa {
    @JsonProperty("approved")
    APPROVED,
    @JsonProperty("manual_review")
    REVIEW,
    @JsonProperty("rejected")
    REJECTED,
    /** Foto inutilizável (sem rosto, borrada, resolução baixa): a pessoa manda outra, sem gastar tentativa. */
    @JsonProperty("retry")
    RETRY
}
