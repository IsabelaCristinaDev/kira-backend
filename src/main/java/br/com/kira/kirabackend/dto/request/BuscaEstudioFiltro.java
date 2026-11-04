package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.domain.enums.OrdenacaoBusca;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;

import java.math.BigDecimal;

/** Todos opcionais. `texto` procura em nome, descrição, especialidades e nome dos serviços. */
public record BuscaEstudioFiltro(
        String texto,
        String cidade,
        TipoEstabelecimento tipo,
        String servico,
        CategoriaServico categoria,
        BigDecimal precoMaximo,
        Double notaMinima,
        Double distanciaMaximaKm,
        OrdenacaoBusca ordenacao
) {}
