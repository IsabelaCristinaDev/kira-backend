package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocaSenhaRequest(

        @NotBlank(message = "Senha atual é obrigatória")
        String senhaAtual,

        // Mesma regra de senha do cadastro
        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres")
        String novaSenha
) {}
