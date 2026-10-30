package br.com.kira.kirabackend.dto.response;

import java.util.List;

public record AvaliacoesResumoResponse(
        Double media,
        int total,
        List<NotaQuantidadeResponse> distribuicao
) {}
