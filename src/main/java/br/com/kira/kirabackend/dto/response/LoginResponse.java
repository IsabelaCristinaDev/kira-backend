package br.com.kira.kirabackend.dto.response;

import java.util.UUID;

public record LoginResponse(
        String token,
        String refreshToken,
        String tipo,
        String nome,
        String email,
        UUID id
) {}