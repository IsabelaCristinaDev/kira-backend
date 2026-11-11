package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.ComissaoAgendamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ComissaoResponse(
        UUID id,
        UUID agendamentoId,
        LocalDateTime dataHoraAtendimento,
        String nomeCliente,
        String nomeServico,
        BigDecimal valorServico,
        BigDecimal percentualComissao,
        BigDecimal valorComissao,
        LocalDateTime dataCalculo
) {
    /** Lê as relações LAZY: precisa ser chamado dentro da transação. */
    public static ComissaoResponse from(ComissaoAgendamento c) {
        Agendamento a = c.getAgendamento();
        return new ComissaoResponse(
                c.getId(),
                a.getId(),
                a.getDataHoraInicio(),
                a.getCliente().getNome(),
                a.getServico().getNome(),
                c.getValorServico(),
                c.getPercentualComissao(),
                c.getValorComissao(),
                c.getDataCalculo()
        );
    }
}
