package br.com.kira.kirabackend.dto.response;

import java.util.UUID;

public record FuncionariaResponse(
        UUID id,
        String nome,
        String fotoUrl,
        String especialidades,
        Boolean ativo
) {}