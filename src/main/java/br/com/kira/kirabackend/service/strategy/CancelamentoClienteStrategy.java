package br.com.kira.kirabackend.service.strategy;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CancelamentoClienteStrategy implements CancelamentoStrategy {

    @Override
    public void validarCancelamento(Agendamento agendamento) {
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            throw new RegraDeNegocioException("Agendamento já está cancelado");
        }

        if (agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Não é possível cancelar um agendamento concluído");
        }

        if (agendamento.getDataHoraInicio().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new RegraDeNegocioException(
                    "Cancelamento pelo cliente deve ser feito com no mínimo 2 horas de antecedência");
        }
    }
}