package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecuperacaoSenhaRequest(

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email
) {}