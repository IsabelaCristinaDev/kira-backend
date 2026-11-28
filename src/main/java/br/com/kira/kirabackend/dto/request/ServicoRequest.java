package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.CategoriaServico;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ServicoRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,

        @Size(max = 500, message = "Descrição deve ter no máximo 500 caracteres")
        String descricao,

        @NotNull(message = "Duração é obrigatória")
        @Positive(message = "Duração deve ser maior que zero")
        Integer duracaoMinutos,

        @NotNull(message = "Preço é obrigatório")
        @Positive(message = "Preço deve ser maior que zero")
        BigDecimal preco,
        // Opcional: sem ela, a categoria é deduzida pelo nome
        CategoriaServico categoria
) {}