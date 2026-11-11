package br.com.kira.kirabackend.dto.response;

import java.time.LocalDateTime;

public record ConsentimentoProvadorResponse(
        boolean ativo,
        String versao,
        LocalDateTime aceitoEm
) {}
