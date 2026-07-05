package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MensagemRequest(

        @NotNull(message = "Agendamento é obrigatório")
        UUID agendamentoId,

        @NotBlank(message = "Conteúdo é obrigatório")
        String conteudo
) {}