package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.entity.Agendamento;
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
) {
    public static AgendamentoResponse from(Agendamento a) {
        return new AgendamentoResponse(
                a.getId(),
                a.getCliente().getId(),
                a.getCliente().getNome(),
                a.getEmpresa().getId(),
                a.getEmpresa().getNome(),
                a.getFuncionaria() != null ? a.getFuncionaria().getId() : null,
                a.getFuncionaria() != null ? a.getFuncionaria().getNome() : null,
                a.getServico().getId(),
                a.getServico().getNome(),
                a.getServico().getDuracaoMinutos(),
                a.getDataHoraInicio(),
                a.getDataHoraFim(),
                a.getStatus(),
                a.getFormaPagamento(),
                a.getObservacoes()
        );
    }
}