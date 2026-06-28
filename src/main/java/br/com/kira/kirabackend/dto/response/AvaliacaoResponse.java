package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.TipoAvaliador;

import java.time.LocalDateTime;
import java.util.UUID;

public record AvaliacaoResponse(
        UUID id,
        UUID agendamentoId,
        UUID clienteId,
        String nomeCliente,
        UUID empresaId,
        String nomeEmpresa,
        TipoAvaliador tipoAvaliador,
        Integer nota,
        String comentario,
        LocalDateTime dataCriacao
) {}