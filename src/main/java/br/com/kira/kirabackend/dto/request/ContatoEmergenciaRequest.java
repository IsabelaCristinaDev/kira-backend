package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContatoEmergenciaRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 150, message = "Nome deve ter no máximo 150 caracteres")
        String nome,
        @NotBlank(message = "Telefone é obrigatório")
        @Size(max = 20, message = "Telefone deve ter no máximo 20 caracteres")
        String telefone,
        @Size(max = 50, message = "Parentesco deve ter no máximo 50 caracteres")
        String parentesco
) {}
