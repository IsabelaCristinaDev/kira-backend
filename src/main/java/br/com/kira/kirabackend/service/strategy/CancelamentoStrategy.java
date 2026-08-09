package br.com.kira.kirabackend.service.strategy;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;

public interface CancelamentoStrategy {
    TipoUsuario getTipo();
    void validarCancelamento(Agendamento agendamento);

    default void validarStatusCancelavel(Agendamento agendamento) {
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            throw new RegraDeNegocioException("Agendamento já está cancelado");
        }

        if (agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Não é possível cancelar um agendamento concluído");
        }
    }
}
