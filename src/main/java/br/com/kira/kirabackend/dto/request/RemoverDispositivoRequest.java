package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RemoverDispositivoRequest(
        @NotBlank(message = "Endereço do navegador é obrigatório")
        String endpoint
) {}
