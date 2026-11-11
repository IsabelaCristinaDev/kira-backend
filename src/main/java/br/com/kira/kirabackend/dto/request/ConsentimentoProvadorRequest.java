package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConsentimentoProvadorRequest(

        @NotBlank(message = "Versão do termo é obrigatória")
        @Size(max = 20, message = "Versão do termo inválida")
        String versao
) {}
