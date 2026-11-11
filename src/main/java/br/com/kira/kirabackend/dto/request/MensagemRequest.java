package br.com.kira.kirabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MensagemRequest(

        @NotNull(message = "Agendamento é obrigatório")
        UUID agendamentoId,

        @NotBlank(message = "Conteúdo é obrigatório")
        @Size(max = 1000, message = "A mensagem pode ter no máximo 1000 caracteres")
        String conteudo
) {}