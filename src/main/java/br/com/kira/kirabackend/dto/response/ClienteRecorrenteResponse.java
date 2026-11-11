package br.com.kira.kirabackend.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ClienteRecorrenteResponse(
        UUID clienteId,
        String nome,
        int totalAtendimentos,
        BigDecimal faturamentoGerado
) {}