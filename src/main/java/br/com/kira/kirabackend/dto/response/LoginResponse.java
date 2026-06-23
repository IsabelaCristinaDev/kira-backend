package br.com.kira.kirabackend.dto.response;

public record LoginResponse(
        String token,
        String tipo,
        String nome,
        String email
) {}