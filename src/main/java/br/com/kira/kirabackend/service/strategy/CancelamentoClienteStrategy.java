package br.com.kira.kirabackend.service.strategy;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CancelamentoClienteStrategy implements CancelamentoStrategy {

    @Override
    public TipoUsuario getTipo() {
        return TipoUsuario.CLIENTE;
    }

    @Override
    public void validarCancelamento(Agendamento agendamento) {
        validarStatusCancelavel(agendamento);

        if (agendamento.getDataHoraInicio().isBefore(LocalDateTime.now(KiraTimeZone.DEFAULT).plusHours(2))) {
            throw new RegraDeNegocioException(
                    "Cancelamento pelo cliente deve ser feito com no mínimo 2 horas de antecedência");
        }
    }
}