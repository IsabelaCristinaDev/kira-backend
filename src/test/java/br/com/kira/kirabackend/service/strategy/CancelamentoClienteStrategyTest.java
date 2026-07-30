package br.com.kira.kirabackend.service.strategy;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class CancelamentoClienteStrategyTest {

    private CancelamentoClienteStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CancelamentoClienteStrategy();
    }

    @Test
    @DisplayName("Deve cancelar agendamento com mais de 2 horas de antecedência")
    void deveCancelarComAntecedenciaSuficiente() {
        Agendamento agendamento = new Agendamento();
        agendamento.setStatus(StatusAgendamento.PENDENTE);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(3));

        assertDoesNotThrow(() -> strategy.validarCancelamento(agendamento));
    }

    @Test
    @DisplayName("Não deve cancelar agendamento com menos de 2 horas de antecedência")
    void naoDeveCancelarComAntecedenciaInsuficiente() {
        Agendamento agendamento = new Agendamento();
        agendamento.setStatus(StatusAgendamento.PENDENTE);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(1));

        assertThrows(RegraDeNegocioException.class,
                () -> strategy.validarCancelamento(agendamento));
    }

    @Test
    @DisplayName("Não deve cancelar agendamento já cancelado")
    void naoDeveCancelarAgendamentoJaCancelado() {
        Agendamento agendamento = new Agendamento();
        agendamento.setStatus(StatusAgendamento.CANCELADO);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(5));

        assertThrows(RegraDeNegocioException.class,
                () -> strategy.validarCancelamento(agendamento));
    }

    @Test
    @DisplayName("Não deve cancelar agendamento já concluído")
    void naoDeveCancelarAgendamentoConcluido() {
        Agendamento agendamento = new Agendamento();
        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        agendamento.setDataHoraInicio(LocalDateTime.now().plusHours(5));

        assertThrows(RegraDeNegocioException.class,
                () -> strategy.validarCancelamento(agendamento));
    }
}