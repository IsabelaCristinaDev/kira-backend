package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendaFuncionariaServiceTest {

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @InjectMocks
    private AgendaFuncionariaService service;

    private Funcionaria funcionaria;
    private Cliente cliente;
    private Empresa empresa;
    private Servico servico;

    @BeforeEach
    void setUp() {
        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio");

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Cliente");

        servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setNome("Design de sobrancelha");
        servico.setDuracaoMinutos(60);
        servico.setEmpresa(empresa);

        funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setNome("Ana");
        funcionaria.setEmpresa(empresa);
    }

    private Agendamento agendamentoEm(LocalDateTime inicio) {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setCliente(cliente);
        a.setEmpresa(empresa);
        a.setServico(servico);
        a.setFuncionaria(funcionaria);
        a.setDataHoraInicio(inicio);
        a.setDataHoraFim(inicio.plusMinutes(60));
        a.setStatus(StatusAgendamento.CONFIRMADO);
        return a;
    }

    @Test
    @DisplayName("Agenda do dia busca da meia-noite até o último instante do dia, sem perder o atendimento das 23h")
    void diaVaiDaMeiaNoiteAoFimDoDia() {
        // Arrange
        LocalDate dia = LocalDate.of(2026, 11, 16);
        LocalDateTime inicioDoDia = LocalDateTime.of(2026, 11, 16, 0, 0);
        LocalDateTime fimDoDia = LocalDateTime.of(dia, LocalTime.MAX);
        Agendamento cedo = agendamentoEm(dia.atTime(9, 0));
        Agendamento tarde = agendamentoEm(dia.atTime(23, 0));
        when(agendamentoRepository.findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), inicioDoDia, fimDoDia)).thenReturn(List.of(cedo, tarde));

        // Act
        List<AgendamentoResponse> agenda = service.listarPorDia(funcionaria.getId(), dia);

        // Assert
        verify(agendamentoRepository).findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), inicioDoDia, fimDoDia);
        assertEquals(List.of(cedo.getId(), tarde.getId()), agenda.stream().map(AgendamentoResponse::id).toList());
    }

    @Test
    @DisplayName("Agenda da semana cobre 7 dias: do início do dia escolhido até o fim do sexto dia depois")
    void semanaCobreSeteDias() {
        // Arrange
        LocalDate segunda = LocalDate.of(2026, 11, 16);
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 16, 0, 0);
        LocalDateTime fim = LocalDateTime.of(LocalDate.of(2026, 11, 22), LocalTime.MAX);
        Agendamento domingoANoite = agendamentoEm(LocalDateTime.of(2026, 11, 22, 23, 0));
        when(agendamentoRepository.findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), inicio, fim)).thenReturn(List.of(domingoANoite));

        // Act
        List<AgendamentoResponse> agenda = service.listarPorSemana(funcionaria.getId(), segunda);

        // Assert
        verify(agendamentoRepository).findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), inicio, fim);
        assertEquals(1, agenda.size());
        assertEquals(domingoANoite.getId(), agenda.get(0).id());
    }

    @Test
    @DisplayName("Semana que atravessa a virada do mês termina no dia certo do mês seguinte")
    void semanaNaViradaDoMes() {
        // Arrange
        LocalDate sabado = LocalDate.of(2026, 11, 28);
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 28, 0, 0);
        LocalDateTime fim = LocalDateTime.of(LocalDate.of(2026, 12, 4), LocalTime.MAX);
        when(agendamentoRepository.findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), inicio, fim)).thenReturn(List.of());

        // Act
        service.listarPorSemana(funcionaria.getId(), sabado);

        // Assert
        verify(agendamentoRepository).findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), inicio, fim);
    }

    @Test
    @DisplayName("Dia sem atendimentos devolve a agenda vazia, sem erro")
    void diaSemAtendimentos() {
        // Arrange
        LocalDate dia = LocalDate.of(2026, 11, 17);
        when(agendamentoRepository.findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), dia.atStartOfDay(), dia.atTime(LocalTime.MAX))).thenReturn(List.of());

        // Act
        List<AgendamentoResponse> agenda = service.listarPorDia(funcionaria.getId(), dia);

        // Assert
        assertTrue(agenda.isEmpty());
    }

    @Test
    @DisplayName("Cada item da agenda traz cliente, serviço, funcionária e horários do atendimento")
    void itemDaAgendaTrazOsDados() {
        // Arrange
        LocalDate dia = LocalDate.of(2026, 11, 16);
        Agendamento atendimento = agendamentoEm(dia.atTime(14, 0));
        when(agendamentoRepository.findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), dia.atStartOfDay(), dia.atTime(LocalTime.MAX))).thenReturn(List.of(atendimento));

        // Act
        AgendamentoResponse item = service.listarPorDia(funcionaria.getId(), dia).get(0);

        // Assert
        assertEquals("Cliente", item.nomeCliente());
        assertEquals("Design de sobrancelha", item.nomeServico());
        assertEquals(funcionaria.getId(), item.funcionariaId());
        assertEquals("Ana", item.nomeFuncionaria());
        assertEquals(dia.atTime(14, 0), item.dataHoraInicio());
        assertEquals(dia.atTime(15, 0), item.dataHoraFim());
        assertEquals(StatusAgendamento.CONFIRMADO, item.status());
    }
}
