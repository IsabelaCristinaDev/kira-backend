package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.FormaPagamento;

/** O pagamento é feito no estúdio: ao concluir, o estúdio registra como a cliente pagou. */
public record ConcluirAtendimentoRequest(

        FormaPagamento formaPagamento
) {}
