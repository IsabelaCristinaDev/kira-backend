package br.com.kira.kirabackend.dto.response;

public record LoginResponse(
        String token,
        String refreshToken,
        String tipo,
        String nome,
        String email
) {}