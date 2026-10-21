package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.MotivoDenuncia;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DenunciaRequest(

        @NotNull(message = "Usuário denunciado é obrigatório")
        UUID denunciadoId,

        UUID agendamentoId,

        @NotNull(message = "Motivo é obrigatório")
        MotivoDenuncia motivo,

        @Size(max = 2000, message = "Descrição deve ter no máximo 2000 caracteres")
        String descricao,

        @NotNull(message = "Informe se há risco imediato")
        Boolean riscoImediato
) {}
