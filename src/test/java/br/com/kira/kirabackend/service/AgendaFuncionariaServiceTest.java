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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendaFuncionariaServiceTest {

    /** Último instante do dia: 23:59:59.999999999. */
    private static final LocalTime FIM_DO_DIA = LocalTime.MAX;

    @Mock
    private AgendamentoRepository agendamentoRepository;

    private AgendaFuncionariaService service;

    private Funcionaria funcionaria;
    private Cliente cliente;
    private Empresa empresa;
    private Servico servico;

    @BeforeEach
    void setUp() {
        service = new AgendaFuncionariaService(agendamentoRepository);

        empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio");

        funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setNome("Ana");
        funcionaria.setEmpresa(empresa);

        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Cliente");

        servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setNome("Design de sobrancelha");
        servico.setDuracaoMinutos(60);
        servico.setEmpresa(empresa);
    }

    private Agendamento agendamento(LocalDateTime inicio) {
        Agendamento agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setFuncionaria(funcionaria);
        agendamento.setServico(servico);
        agendamento.setDataHoraInicio(inicio);
        agendamento.setDataHoraFim(inicio.plusMinutes(60));
        agendamento.setStatus(StatusAgendamento.CONFIRMADO);
        return agendamento;
    }

    private void mockarIntervalo(LocalDateTime inicio, LocalDateTime fim, List<Agendamento> agendamentos) {
        when(agendamentoRepository.findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                funcionaria.getId(), inicio, fim)).thenReturn(agendamentos);
    }

    @Nested
    @DisplayName("listarPorDia")
    class ListarPorDia {

        @Test
        @DisplayName("Busca do primeiro ao último instante do dia e devolve os agendamentos convertidos, na ordem do banco")
        void deveListarODiaInteiro() {
            LocalDate dia = LocalDate.of(2026, 11, 16);
            Agendamento manha = agendamento(dia.atTime(9, 0));
            Agendamento noite = agendamento(dia.atTime(19, 30));
            mockarIntervalo(dia.atStartOfDay(), dia.atTime(FIM_DO_DIA), List.of(manha, noite));

            List<AgendamentoResponse> agenda = service.listarPorDia(funcionaria.getId(), dia);

            assertEquals(List.of(manha.getId(), noite.getId()),
                    agenda.stream().map(AgendamentoResponse::id).toList());
            AgendamentoResponse primeiro = agenda.get(0);
            assertEquals(funcionaria.getId(), primeiro.funcionariaId());
            assertEquals(cliente.getNome(), primeiro.nomeCliente());
            assertEquals(servico.getNome(), primeiro.nomeServico());
            assertEquals(dia.atTime(9, 0), primeiro.dataHoraInicio());
        }

        @Test
        @DisplayName("O intervalo do dia vai de 00:00 até 23:59:59.999999999, sem invadir o dia seguinte")
        void intervaloNaoInvadeODiaSeguinte() {
            LocalDate dia = LocalDate.of(2026, 11, 16);

            service.listarPorDia(funcionaria.getId(), dia);

            verify(agendamentoRepository).findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                    funcionaria.getId(),
                    LocalDateTime.of(2026, 11, 16, 0, 0),
                    LocalDateTime.of(2026, 11, 16, 23, 59, 59, 999_999_999));
        }

        @Test
        @DisplayName("Dia sem atendimentos devolve lista vazia")
        void diaSemAtendimentos() {
            LocalDate dia = LocalDate.of(2026, 11, 16);
            mockarIntervalo(dia.atStartOfDay(), dia.atTime(FIM_DO_DIA), List.of());

            assertTrue(service.listarPorDia(funcionaria.getId(), dia).isEmpty());
        }
    }

    @Nested
    @DisplayName("listarPorSemana")
    class ListarPorSemana {

        @Test
        @DisplayName("A semana tem 7 dias: do início do primeiro dia até o fim do sexto dia seguinte")
        void semanaTemSeteDias() {
            LocalDate inicio = LocalDate.of(2026, 11, 16);

            service.listarPorSemana(funcionaria.getId(), inicio);

            // Fim no dia 22, não no 23: senão a semana teria 8 dias
            verify(agendamentoRepository).findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                    funcionaria.getId(),
                    LocalDateTime.of(2026, 11, 16, 0, 0),
                    LocalDateTime.of(2026, 11, 22, 23, 59, 59, 999_999_999));
        }

        @Test
        @DisplayName("Devolve os agendamentos da semana convertidos, na ordem do banco")
        void deveListarASemana() {
            LocalDate inicio = LocalDate.of(2026, 11, 16);
            Agendamento primeiroDia = agendamento(inicio.atTime(10, 0));
            Agendamento ultimoDia = agendamento(inicio.plusDays(6).atTime(17, 0));
            mockarIntervalo(inicio.atStartOfDay(), inicio.plusDays(6).atTime(FIM_DO_DIA),
                    List.of(primeiroDia, ultimoDia));

            List<AgendamentoResponse> agenda = service.listarPorSemana(funcionaria.getId(), inicio);

            assertEquals(List.of(primeiroDia.getId(), ultimoDia.getId()),
                    agenda.stream().map(AgendamentoResponse::id).toList());
            assertEquals(inicio.plusDays(6).atTime(17, 0), agenda.get(1).dataHoraInicio());
        }

        @Test
        @DisplayName("Semana que atravessa a virada do ano termina no dia certo do ano seguinte")
        void semanaNaViradaDoAno() {
            LocalDate inicio = LocalDate.of(2026, 12, 28);

            service.listarPorSemana(funcionaria.getId(), inicio);

            verify(agendamentoRepository).findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                    eq(funcionaria.getId()),
                    eq(LocalDateTime.of(2026, 12, 28, 0, 0)),
                    eq(LocalDateTime.of(2027, 1, 3, 23, 59, 59, 999_999_999)));
        }

        @Test
        @DisplayName("Semana sem atendimentos devolve lista vazia")
        void semanaSemAtendimentos() {
            when(agendamentoRepository.findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                    eq(funcionaria.getId()), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            assertTrue(service.listarPorSemana(funcionaria.getId(), LocalDate.of(2026, 11, 16)).isEmpty());
        }
    }
}
