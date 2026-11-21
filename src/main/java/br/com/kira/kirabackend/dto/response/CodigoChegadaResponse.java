package br.com.kira.kirabackend.dto.response;

import java.time.OffsetDateTime;

/** O código que a cliente mostra ao chegar e até quando ele vale (com o fuso, para o app contar o tempo certo). */
public record CodigoChegadaResponse(String codigo, OffsetDateTime expiraEm) {
}
