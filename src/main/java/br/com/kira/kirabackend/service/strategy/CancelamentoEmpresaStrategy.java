package br.com.kira.kirabackend.service.strategy;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import org.springframework.stereotype.Component;

@Component
public class CancelamentoEmpresaStrategy implements CancelamentoStrategy {

    @Override
    public TipoUsuario getTipo() {
        return TipoUsuario.EMPRESA;
    }

    @Override
    public void validarCancelamento(Agendamento agendamento) {
        validarStatusCancelavel(agendamento);
    }
}