package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.ComissaoAgendamento;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.dto.response.ComissaoResponse;
import br.com.kira.kirabackend.repository.ComissaoAgendamentoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComissaoServiceTest {

    @Mock
    private ComissaoAgendamentoRepository comissaoRepository;

    @InjectMocks
    private ComissaoService service;

    private Agendamento agendamento(String preco, String percentual) {
        Servico servico = new Servico();
        servico.setId(UUID.randomUUID());
        servico.setPreco(new BigDecimal(preco));

        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setComissaoPercentual(percentual != null ? new BigDecimal(percentual) : null);

        Agendamento agendamento = new Agendamento();
        agendamento.setId(UUID.randomUUID());
        agendamento.setServico(servico);
        agendamento.setFuncionaria(funcionaria);
        return agendamento;
    }

    private ComissaoAgendamento comissaoSalva() {
        ArgumentCaptor<ComissaoAgendamento> captor = ArgumentCaptor.forClass(ComissaoAgendamento.class);
        verify(comissaoRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Serviço de R$ 80 com 30% gera comissão de R$ 24,00")
    void deveCalcularValorDaComissao() {
        Agendamento agendamento = agendamento("80.00", "30");

        service.calcularERegistrarComissao(agendamento);

        ComissaoAgendamento comissao = comissaoSalva();
        assertEquals(new BigDecimal("24.00"), comissao.getValorComissao());
        assertEquals(new BigDecimal("80.00"), comissao.getValorServico());
        assertEquals(new BigDecimal("30"), comissao.getPercentualComissao());
        assertSame(agendamento, comissao.getAgendamento());
        assertSame(agendamento.getFuncionaria(), comissao.getFuncionaria());
    }

    @ParameterizedTest(name = "R$ {0} com {1}% = R$ {2}")
    @CsvSource({
            "33.33, 15, 5.00",   // 4,9995 -> 5,00
            "10.00, 12.5, 1.25",
            "19.99, 10, 2.00",   // 1,999 -> 2,00
            "0.10, 5, 0.01",     // 0,005 -> 0,01 (HALF_UP sobe no 5)
            "0.10, 4, 0.00"      // 0,004 -> 0,00
    })
    @DisplayName("Arredonda o valor da comissão para 2 casas com HALF_UP")
    void deveArredondarComHalfUp(String preco, String percentual, String esperado) {
        service.calcularERegistrarComissao(agendamento(preco, percentual));

        ComissaoAgendamento comissao = comissaoSalva();
        assertEquals(new BigDecimal(esperado), comissao.getValorComissao());
        assertEquals(2, comissao.getValorComissao().scale());
    }

    @Test
    @DisplayName("Agendamento sem funcionária não gera comissão")
    void semFuncionariaNaoGeraComissao() {
        Agendamento agendamento = agendamento("80.00", "30");
        agendamento.setFuncionaria(null);

        service.calcularERegistrarComissao(agendamento);

        verifyNoInteractions(comissaoRepository);
    }

    @Test
    @DisplayName("Funcionária sem percentual de comissão não gera comissão")
    void funcionariaSemPercentualNaoGeraComissao() {
        service.calcularERegistrarComissao(agendamento("80.00", null));

        verifyNoInteractions(comissaoRepository);
    }

    @Test
    @DisplayName("Total da funcionária é 0 quando ela não tem comissões")
    void totalZeroSemComissoes() {
        UUID funcionariaId = UUID.randomUUID();
        // SUM sem linhas devolve null no banco
        when(comissaoRepository.calcularTotalComissaoFuncionaria(funcionariaId)).thenReturn(null);

        assertEquals(BigDecimal.ZERO, service.totalComissaoFuncionaria(funcionariaId));
    }

    @Test
    @DisplayName("Total da funcionária devolve a soma calculada pelo repositório")
    void totalDevolveSomaDoRepositorio() {
        UUID funcionariaId = UUID.randomUUID();
        when(comissaoRepository.calcularTotalComissaoFuncionaria(funcionariaId))
                .thenReturn(new BigDecimal("129.50"));

        assertEquals(new BigDecimal("129.50"), service.totalComissaoFuncionaria(funcionariaId));
    }

    @Test
    @DisplayName("Lista as comissões da funcionária como DTO, com cliente, serviço e data do atendimento")
    void listaComoDto() {
        Agendamento agendamento = agendamento("80.00", "30");
        Cliente cliente = new Cliente();
        cliente.setNome("Ana");
        agendamento.setCliente(cliente);
        agendamento.getServico().setNome("Escova");
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 20, 14, 0);
        agendamento.setDataHoraInicio(inicio);

        ComissaoAgendamento comissao = new ComissaoAgendamento();
        comissao.setId(UUID.randomUUID());
        comissao.setAgendamento(agendamento);
        comissao.setFuncionaria(agendamento.getFuncionaria());
        comissao.setValorServico(new BigDecimal("80.00"));
        comissao.setPercentualComissao(new BigDecimal("30"));
        comissao.setValorComissao(new BigDecimal("24.00"));

        UUID funcionariaId = agendamento.getFuncionaria().getId();
        when(comissaoRepository.findByFuncionariaId(funcionariaId)).thenReturn(List.of(comissao));

        List<ComissaoResponse> lista = service.listarPorFuncionaria(funcionariaId);

        assertEquals(1, lista.size());
        ComissaoResponse dto = lista.get(0);
        assertEquals(comissao.getId(), dto.id());
        assertEquals(agendamento.getId(), dto.agendamentoId());
        assertEquals(inicio, dto.dataHoraAtendimento());
        assertEquals("Ana", dto.nomeCliente());
        assertEquals("Escova", dto.nomeServico());
        assertEquals(new BigDecimal("24.00"), dto.valorComissao());
    }
}
