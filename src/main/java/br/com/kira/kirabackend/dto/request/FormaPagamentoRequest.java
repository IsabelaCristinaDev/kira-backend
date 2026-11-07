package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.FormaPagamento;
import jakarta.validation.constraints.NotNull;

/** Correção feita pelo estúdio quando registrou errado como a cliente pagou. */
public record FormaPagamentoRequest(

        @NotNull(message = "Forma de pagamento é obrigatória")
        FormaPagamento formaPagamento
) {}
