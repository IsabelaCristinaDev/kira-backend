package br.com.kira.kirabackend.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ServicoRankingResponse(
        UUID servicoId,
        String nome,
        int totalAtendimentos,
        BigDecimal faturamento,
        List<ClienteDoServicoResponse> clientes
) {}
