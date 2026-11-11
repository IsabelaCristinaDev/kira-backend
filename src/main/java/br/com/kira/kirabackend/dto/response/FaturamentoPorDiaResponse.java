package br.com.kira.kirabackend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FaturamentoPorDiaResponse(
        LocalDate data,
        BigDecimal faturamento,
        int totalAtendimentos
) {}
