package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import br.com.kira.kirabackend.dto.response.ClienteDoServicoResponse;
import br.com.kira.kirabackend.dto.response.DashboardEmpresaResponse;
import br.com.kira.kirabackend.dto.response.FaturamentoPorDiaResponse;
import br.com.kira.kirabackend.dto.response.NotaQuantidadeResponse;
import br.com.kira.kirabackend.dto.response.ServicoRankingResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardEmpresaServiceTest {

    // Período de 7 dias: o anterior vai de 22 a 28/09
    private static final LocalDate INICIO = LocalDate.of(2026, 9, 29);
    private static final LocalDate FIM = LocalDate.of(2026, 10, 5);

    @Mock
    private AgendamentoRepository agendamentoRepository;

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @InjectMocks
    private DashboardEmpresaService service;

    private final UUID empresaId = UUID.randomUUID();
    private Servico unha;
    private Servico cilios;
    private Cliente ana;
    private Cliente bia;

    @BeforeEach
    void setUp() {
        unha = servico("Unha", "50.00");
        cilios = servico("Cílios", "120.00");
        ana = cliente("Ana");
        bia = cliente("Bia");
        lenient().when(avaliacaoRepository.findByEmpresaIdAndTipoAvaliador(empresaId, TipoAvaliador.CLIENTE))
                .thenReturn(List.of());
        mockarAnterior();
    }

    private static Servico servico(String nome, String preco) {
        Servico s = new Servico();
        s.setId(UUID.randomUUID());
        s.setNome(nome);
        s.setPreco(new BigDecimal(preco));
        return s;
    }

    private static Cliente cliente(String nome) {
        Cliente c = new Cliente();
        c.setId(UUID.randomUUID());
        c.setNome(nome);
        return c;
    }

    private static Agendamento concluido(Servico servico, Cliente cliente, LocalDateTime inicio) {
        Agendamento a = new Agendamento();
        a.setId(UUID.randomUUID());
        a.setServico(servico);
        a.setCliente(cliente);
        a.setDataHoraInicio(inicio);
        a.setStatus(StatusAgendamento.CONCLUIDO);
        return a;
    }

    private static Avaliacao avaliacao(int nota) {
        Avaliacao a = new Avaliacao();
        a.setNota(nota);
        return a;
    }

    private static LocalDateTime dia(int mes, int dia, int hora) {
        return LocalDateTime.of(2026, mes, dia, hora, 0);
    }

    private void mockarPeriodo(Agendamento... agendamentos) {
        when(agendamentoRepository.findByEmpresaIdAndStatusAndDataHoraInicioBetween(
                empresaId, StatusAgendamento.CONCLUIDO, INICIO.atStartOfDay(), FIM.atTime(LocalTime.MAX)))
                .thenReturn(Arrays.asList(agendamentos));
    }

    private void mockarAnterior(Agendamento... agendamentos) {
        lenient().when(agendamentoRepository.findByEmpresaIdAndStatusAndDataHoraInicioBetween(
                empresaId, StatusAgendamento.CONCLUIDO,
                LocalDate.of(2026, 9, 22).atStartOfDay(), LocalDate.of(2026, 9, 28).atTime(LocalTime.MAX)))
                .thenReturn(Arrays.asList(agendamentos));
    }

    private void mockarCancelados(long total) {
        when(agendamentoRepository.countByEmpresaIdAndStatusAndDataHoraInicioBetween(
                eq(empresaId), eq(StatusAgendamento.CANCELADO), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(total);
    }

    private DashboardEmpresaResponse gerar() {
        return service.gerar(empresaId, INICIO, FIM);
    }

    @Test
    @DisplayName("Data de fim antes do início dá erro")
    void fimAntesDoInicio() {
        LocalDate fim = INICIO.minusDays(1);

        RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(empresaId, INICIO, fim));
        assertEquals("A data de fim deve ser igual ou posterior à data de início", ex.getMessage());
        verifyNoInteractions(agendamentoRepository, avaliacaoRepository);
    }

    @Test
    @DisplayName("Sem atendimentos, os números ficam zerados e a variação fica nula")
    void semAtendimentos() {
        mockarPeriodo();
        mockarCancelados(0);

        DashboardEmpresaResponse r = gerar();

        assertEquals(BigDecimal.ZERO, r.faturamentoTotal());
        assertEquals(BigDecimal.ZERO, r.ticketMedio());
        assertEquals(0.0, r.taxaConclusao());
        assertEquals(BigDecimal.ZERO, r.faturamentoPeriodoAnterior());
        assertNull(r.variacaoFaturamento());
        assertEquals(List.of(), r.faturamentoPorDia());
        assertEquals(List.of(), r.rankingServicos());
        assertNull(r.avaliacoes().media());
        assertEquals(0, r.avaliacoes().total());
    }

    @Nested
    @DisplayName("Faturamento e atendimentos")
    class Faturamento {

        @Test
        @DisplayName("Soma faturamento, ticket médio e taxa de conclusão com os cancelados")
        void totais() {
            mockarPeriodo(
                    concluido(unha, ana, dia(9, 29, 9)),
                    concluido(unha, bia, dia(9, 29, 10)),
                    concluido(cilios, ana, dia(10, 1, 14)));
            mockarCancelados(1);

            DashboardEmpresaResponse r = gerar();

            assertEquals(new BigDecimal("220.00"), r.faturamentoTotal());
            assertEquals(3, r.totalAtendimentos());
            assertEquals(new BigDecimal("73.33"), r.ticketMedio());
            assertEquals(1, r.totalCancelados());
            assertEquals(75.0, r.taxaConclusao());
        }

        @Test
        @DisplayName("Agrupa o faturamento por dia, em ordem de data")
        void porDia() {
            mockarPeriodo(
                    concluido(cilios, ana, dia(10, 1, 14)),
                    concluido(unha, ana, dia(9, 29, 9)),
                    concluido(unha, bia, dia(9, 29, 10)));
            mockarCancelados(0);

            assertEquals(List.of(
                    new FaturamentoPorDiaResponse(LocalDate.of(2026, 9, 29), new BigDecimal("100.00"), 2),
                    new FaturamentoPorDiaResponse(LocalDate.of(2026, 10, 1), new BigDecimal("120.00"), 1)),
                    gerar().faturamentoPorDia());
        }

        @Test
        @DisplayName("Compara com o período anterior de mesmo tamanho")
        void variacao() {
            mockarPeriodo(concluido(cilios, ana, dia(10, 1, 14)));
            mockarAnterior(concluido(unha, bia, dia(9, 22, 9)), concluido(unha, bia, dia(9, 28, 9)));
            mockarCancelados(0);

            DashboardEmpresaResponse r = gerar();

            assertEquals(new BigDecimal("100.00"), r.faturamentoPeriodoAnterior());
            assertEquals(20.0, r.variacaoFaturamento());
        }

        @Test
        @DisplayName("Mantém o desempenho por funcionária e a recorrência de clientes")
        void funcionariasERecorrencia() {
            Funcionaria carla = new Funcionaria();
            carla.setId(UUID.randomUUID());
            carla.setNome("Carla");
            Agendamento comCarla = concluido(cilios, ana, dia(10, 1, 14));
            comCarla.setFuncionaria(carla);
            mockarPeriodo(concluido(unha, ana, dia(9, 29, 9)), comCarla, concluido(unha, bia, dia(9, 30, 9)));
            mockarCancelados(0);

            DashboardEmpresaResponse r = gerar();

            assertEquals("Carla", r.desempenhoPorFuncionaria().get(0).nome());
            assertEquals(new BigDecimal("120.00"), r.desempenhoPorFuncionaria().get(0).faturamento());
            assertEquals(2, r.totalClientesUnicos());
            assertEquals(1, r.totalClientesRecorrentes());
            assertEquals(50.0, r.percentualRecorrencia());
        }
    }

    @Nested
    @DisplayName("Ranking de serviços")
    class Ranking {

        @Test
        @DisplayName("Ordena pelos mais feitos e, no empate, pelo faturamento")
        void ordem() {
            mockarPeriodo(
                    concluido(unha, ana, dia(9, 29, 9)),
                    concluido(cilios, ana, dia(9, 30, 9)),
                    concluido(unha, bia, dia(10, 1, 9)),
                    concluido(cilios, bia, dia(10, 2, 9)),
                    concluido(cilios, bia, dia(10, 3, 9)));
            mockarCancelados(0);

            List<ServicoRankingResponse> ranking = gerar().rankingServicos();

            assertEquals(List.of("Cílios", "Unha"), ranking.stream().map(ServicoRankingResponse::nome).toList());
            assertEquals(3, ranking.get(0).totalAtendimentos());
            assertEquals(new BigDecimal("360.00"), ranking.get(0).faturamento());
            assertEquals(cilios.getId(), ranking.get(0).servicoId());
        }

        @Test
        @DisplayName("Lista os clientes de cada serviço com visitas, gasto e último atendimento")
        void clientes() {
            mockarPeriodo(
                    concluido(unha, ana, dia(9, 29, 9)),
                    concluido(unha, bia, dia(9, 30, 9)),
                    concluido(unha, bia, dia(10, 3, 9)));
            mockarCancelados(0);

            List<ClienteDoServicoResponse> clientes = gerar().rankingServicos().get(0).clientes();

            assertEquals(List.of(
                    new ClienteDoServicoResponse(bia.getId(), "Bia", 2, new BigDecimal("100.00"), dia(10, 3, 9)),
                    new ClienteDoServicoResponse(ana.getId(), "Ana", 1, new BigDecimal("50.00"), dia(9, 29, 9))),
                    clientes);
        }
    }

    @Test
    @DisplayName("Resume as avaliações de clientes com média e quantidade por nota, de 5 a 1")
    void avaliacoes() {
        mockarPeriodo();
        mockarCancelados(0);
        when(avaliacaoRepository.findByEmpresaIdAndTipoAvaliador(empresaId, TipoAvaliador.CLIENTE))
                .thenReturn(List.of(avaliacao(5), avaliacao(5), avaliacao(4), avaliacao(2)));

        var resumo = gerar().avaliacoes();

        assertEquals(4.0, resumo.media());
        assertEquals(4, resumo.total());
        assertEquals(List.of(
                new NotaQuantidadeResponse(5, 2),
                new NotaQuantidadeResponse(4, 1),
                new NotaQuantidadeResponse(3, 0),
                new NotaQuantidadeResponse(2, 1),
                new NotaQuantidadeResponse(1, 0)), resumo.distribuicao());
    }
}
