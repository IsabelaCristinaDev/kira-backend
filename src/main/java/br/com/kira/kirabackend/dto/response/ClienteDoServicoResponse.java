package br.com.kira.kirabackend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ClienteDoServicoResponse(
        UUID clienteId,
        String nome,
        int totalAtendimentos,
        BigDecimal totalGasto,
        LocalDateTime ultimoAtendimento
) {}
