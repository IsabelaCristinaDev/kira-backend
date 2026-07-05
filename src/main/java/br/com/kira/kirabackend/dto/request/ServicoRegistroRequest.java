package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ServicoRegistroRequest(
        @NotBlank(message = "Nome do serviço é obrigatório")
        String nome,

        @NotBlank(message = "Preço do serviço é obrigatório")
        String preco,

        @NotBlank(message = "Duração do serviço é obrigatória")
        String duracao
) {}