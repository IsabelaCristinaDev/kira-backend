package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;

import java.math.BigDecimal;
import java.util.UUID;

public record ServicoResponse(
        UUID id,
        String nome,
        String descricao,
        Integer duracaoMinutos,
        BigDecimal preco,
        Boolean ativo,
        CategoriaServico categoria
) {
    public static ServicoResponse from(Servico servico) {
        return new ServicoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getDescricao(),
                servico.getDuracaoMinutos(),
                servico.getPreco(),
                servico.getAtivo(),
                CategoriaServico.escolhidaOuPeloNome(servico.getCategoria(), servico.getNome()));
    }
}