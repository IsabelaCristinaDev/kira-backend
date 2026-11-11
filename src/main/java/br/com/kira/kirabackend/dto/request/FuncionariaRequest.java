package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/** A foto não vem aqui: tem rota própria de envio. Se o corpo trouxer "fotoUrl", o campo é ignorado. */
public record FuncionariaRequest(

    @NotBlank(message = "Nome é obrigatório" )

    String nome,
    String especialidades,

    // Opcional: sem valor, a funcionária não gera comissão.
    // @Digits segue a coluna numeric(5,2), para o banco não arredondar em silêncio.
    @DecimalMin(value = "0", message = "Comissão deve ser de no mínimo 0%")
    @DecimalMax(value = "100", message = "Comissão deve ser de no máximo 100%")
    @Digits(integer = 3, fraction = 2, message = "Comissão deve ter no máximo 2 casas decimais")
    BigDecimal comissaoPercentual


    ){}
