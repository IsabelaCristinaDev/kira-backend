package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;

/** A senha atual confirma que é a própria pessoa pedindo a exclusão da conta. */
public record ExclusaoContaRequest(

        @NotBlank(message = "Informe a sua senha para excluir a conta")
        String senha
) {}
