package br.com.kira.kirabackend.service.strategy;

import br.com.kira.kirabackend.domain.entity.Agendamento;

public interface CancelamentoStrategy {
    void validarCancelamento(Agendamento agendamento);
}
