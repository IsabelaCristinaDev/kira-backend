package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.FormaPagamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;

import java.time.LocalDateTime;
import java.util.UUID;

public record AgendamentoResponse(
        UUID id,
        UUID clienteId,
        String nomeCliente,
        UUID empresaId,
        String nomeEmpresa,
        UUID funcionariaId,
        String nomeFuncionaria,
        UUID servicoId,
        String nomeServico,
        Integer duracaoMinutos,
        LocalDateTime dataHoraInicio,
        LocalDateTime dataHoraFim,
        StatusAgendamento status,
        FormaPagamento formaPagamento,
        String observacoes
) {}