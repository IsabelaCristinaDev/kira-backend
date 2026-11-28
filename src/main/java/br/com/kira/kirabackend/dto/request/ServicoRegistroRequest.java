package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.CategoriaServico;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServicoRegistroRequest(
        @NotBlank(message = "Nome do serviço é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,

        // Preço e duração chegam como texto ("R$ 50,00", "1h30") e são convertidos no AuthService
        @NotBlank(message = "Preço do serviço é obrigatório")
        @Size(max = 50, message = "Preço deve ter no máximo 50 caracteres")
        String preco,

        @NotBlank(message = "Duração do serviço é obrigatória")
        @Size(max = 50, message = "Duração deve ter no máximo 50 caracteres")
        String duracao,
        // Opcional: sem ela, a categoria é deduzida pelo nome
        CategoriaServico categoria
) {}