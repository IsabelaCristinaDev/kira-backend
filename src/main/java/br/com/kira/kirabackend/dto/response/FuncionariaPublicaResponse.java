package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.entity.Funcionaria;

import java.util.UUID;

/**
 * Funcionária vista na vitrine, sem login: só o que a cliente precisa para escolher quem vai atender.
 * Comissão e situação ficam na {@link FuncionariaResponse}, da área do estúdio.
 */
public record FuncionariaPublicaResponse(
        UUID id,
        String nome,
        String fotoUrl,
        String especialidades
) {
    public static FuncionariaPublicaResponse from(Funcionaria funcionaria) {
        return new FuncionariaPublicaResponse(
                funcionaria.getId(),
                funcionaria.getNome(),
                funcionaria.getFotoUrl(),
                funcionaria.getEspecialidades());
    }
}
