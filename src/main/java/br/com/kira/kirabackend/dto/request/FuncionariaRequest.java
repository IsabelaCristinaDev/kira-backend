package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record FuncionariaRequest(

    @NotBlank(message = "Nome é obrigatório" )

    String nome,
    String fotoUrl,
    String especialidades,
    BigDecimal comissaoPercentual


    ){}