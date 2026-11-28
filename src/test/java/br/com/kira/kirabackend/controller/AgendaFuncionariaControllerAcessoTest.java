package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.enums.FormaPagamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.AgendaFuncionariaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O AcessoEmpresa é o real: a funcionária é buscada e a empresa dela comparada com a conta logada.
@WebMvcTest(controllers = AgendaFuncionariaController.class)
@Import({SecurityConfig.class, AcessoEmpresa.class})
class AgendaFuncionariaControllerAcessoTest extends BaseAcessoWeb {

    @MockBean
    private AgendaFuncionariaService agendaFuncionariaService;

    private Empresa empresa;
    private Funcionaria funcionaria;
    private String dia;
    private String semana;

    @BeforeEach
    void setUp() {
        empresa = novaEmpresa();
        funcionaria = funcionariaDa(empresa);
        dia = "/api/funcionarias/" + funcionaria.getId() + "/agenda/dia";
        semana = "/api/funcionarias/" + funcionaria.getId() + "/agenda/semana";
    }

    @Test
    @DisplayName("Sem token, a agenda do dia e da semana respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(get(dia).param("data", "2030-01-10")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(semana).param("dataInicio", "2030-01-07")).andExpect(status().isUnauthorized());
        verifyNoInteractions(agendaFuncionariaService);
    }

    @Test
    @DisplayName("Funcionária de outra empresa ou cliente: 403")
    void outraConta403() throws Exception {
        mockMvc.perform(get(dia).param("data", "2030-01-10").with(token(novaEmpresa())))
                .andExpect(erro(403, "ACESSO_NEGADO", dia));
        mockMvc.perform(get(semana).param("dataInicio", "2030-01-07").with(token(novaCliente())))
                .andExpect(erro(403, "ACESSO_NEGADO", semana));
        verifyNoInteractions(agendaFuncionariaService);
    }

    @Test
    @DisplayName("Funcionária inexistente: 404")
    void funcionariaInexistente404() throws Exception {
        String caminho = "/api/funcionarias/" + UUID.randomUUID() + "/agenda/dia";
        mockMvc.perform(get(caminho).param("data", "2030-01-10").with(token(empresa)))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", caminho));
        verifyNoInteractions(agendaFuncionariaService);
    }

    @Test
    @DisplayName("Sem a data ou com data inválida: 400 de requisição inválida")
    void dataInvalida400() throws Exception {
        mockMvc.perform(get(dia).with(token(empresa))).andExpect(erro(400, "REQUISICAO_INVALIDA", dia));
        mockMvc.perform(get(semana).param("dataInicio", "ontem").with(token(empresa)))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", semana));
        verifyNoInteractions(agendaFuncionariaService);
    }

    @Test
    @DisplayName("A empresa dona vê a agenda do dia e da semana: 200")
    void dona200() throws Exception {
        LocalDateTime inicio = LocalDateTime.of(2030, 1, 10, 14, 0);
        AgendamentoResponse agendamento = new AgendamentoResponse(UUID.randomUUID(), UUID.randomUUID(), "Ana",
                empresa.getId(), "Estúdio", funcionaria.getId(), "Bia", UUID.randomUUID(), "Corte", 60, inicio,
                inicio.plusHours(1), StatusAgendamento.CONFIRMADO, new BigDecimal("80.00"), FormaPagamento.PIX, null);
        when(agendaFuncionariaService.listarPorDia(funcionaria.getId(), LocalDate.of(2030, 1, 10)))
                .thenReturn(List.of(agendamento));
        when(agendaFuncionariaService.listarPorSemana(funcionaria.getId(), LocalDate.of(2030, 1, 7)))
                .thenReturn(List.of(agendamento));

        mockMvc.perform(get(dia).param("data", "2030-01-10").with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].funcionariaId").value(funcionaria.getId().toString()));
        mockMvc.perform(get(semana).param("dataInicio", "2030-01-07").with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
