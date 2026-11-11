package br.com.kira.kirabackend.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record FuncionariaDesempenhoResponse(
        UUID funcionariaId,
        String nome,
        BigDecimal faturamento,
        int totalAtendimentos
) {}