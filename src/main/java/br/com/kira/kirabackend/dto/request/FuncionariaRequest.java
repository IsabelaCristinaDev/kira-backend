package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FuncionariaRequest(

    @NotBlank(message = "Nome é obrigatório" )

    String nome,
    String fotoUrl,
    String especialidade


    ){}