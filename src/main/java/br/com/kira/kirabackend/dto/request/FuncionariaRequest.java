package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** A foto não vem aqui: tem rota própria de envio. Se o corpo trouxer "fotoUrl", o campo é ignorado. */
public record FuncionariaRequest(

    @NotBlank(message = "Nome é obrigatório" )

    String nome,
    String especialidades,
    BigDecimal comissaoPercentual


    ){}
