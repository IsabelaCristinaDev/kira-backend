package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Estúdio na busca: já traz os serviços e o menor preço, sem precisar de uma chamada por estúdio. */
public record EstudioBuscaResponse(
        UUID id,
        String nome,
        String descricao,
        EnderecoResponse endereco,
        String telefone,
        String fotoUrl,
        TipoEstabelecimento tipoEstabelecimento,
        Double mediaAvaliacoes,
        long quantidadeAvaliacoes,
        BigDecimal menorPreco,
        Double latitude,
        Double longitude,
        // Nula sem login de cliente ou sem as coordenadas das duas pontas
        Double distanciaKm,
        List<ServicoResponse> servicos
) {}
