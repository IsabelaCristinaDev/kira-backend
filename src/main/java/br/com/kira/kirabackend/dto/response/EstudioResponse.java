package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;

import java.util.UUID;

public record EstudioResponse(
        UUID id,
        String nome,
        String descricao,
        String endereco,
        String telefone,
        String fotoUrl,
        TipoEstabelecimento tipoEstabelecimento,
        Double mediaAvaliacoes
) {}