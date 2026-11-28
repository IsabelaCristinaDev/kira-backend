package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.dto.response.DashboardEmpresaResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.DashboardEmpresaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O AcessoEmpresa é o real. O GET do painel tem regra própria no SecurityConfig (o resto de /api/empresas é público).
@WebMvcTest(controllers = DashboardEmpresaController.class)
@Import({SecurityConfig.class, AcessoEmpresa.class})
class DashboardEmpresaControllerAcessoTest extends BaseAcessoWeb {

    @MockBean
    private DashboardEmpresaService dashboardEmpresaService;

    private Empresa empresa;
    private String painel;

    @BeforeEach
    void setUp() {
        empresa = novaEmpresa();
        painel = "/api/empresas/" + empresa.getId() + "/dashboard";
    }

    private static DashboardEmpresaResponse painelVazio() {
        return new DashboardEmpresaResponse(new BigDecimal("150.00"), 2, List.of(), 2, 0, 0.0, List.of(), List.of(),
                List.of(), new BigDecimal("75.00"), 0, 100.0, BigDecimal.ZERO, null, List.of(), List.of(), null);
    }

    @Test
    @DisplayName("Sem token ou com token recusado: 401")
    void semToken401() throws Exception {
        mockMvc.perform(get(painel)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(painel).with(tokenInvalido())).andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardEmpresaService);
    }

    @Test
    @DisplayName("Painel de outra empresa ou visto por cliente: 403")
    void outraConta403() throws Exception {
        mockMvc.perform(get(painel).with(token(novaEmpresa()))).andExpect(erro(403, "ACESSO_NEGADO", painel));
        mockMvc.perform(get(painel).with(token(novaCliente()))).andExpect(erro(403, "ACESSO_NEGADO", painel));
        verifyNoInteractions(dashboardEmpresaService);
    }

    @Test
    @DisplayName("Data fora do formato ISO: 400 de requisição inválida")
    void dataInvalida400() throws Exception {
        mockMvc.perform(get(painel).param("dataInicio", "10/01/2030").with(token(empresa)))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", painel));
        verifyNoInteractions(dashboardEmpresaService);
    }

    @Test
    @DisplayName("Fim antes do início: 400 de regra de negócio")
    void periodoInvertido400() throws Exception {
        LocalDate inicio = LocalDate.of(2030, 2, 1);
        LocalDate fim = LocalDate.of(2030, 1, 1);
        when(dashboardEmpresaService.gerar(empresa.getId(), inicio, fim))
                .thenThrow(new RegraDeNegocioException("A data de fim deve ser igual ou posterior à data de início"));

        mockMvc.perform(get(painel).param("dataInicio", "2030-02-01").param("dataFim", "2030-01-01")
                        .with(token(empresa)))
                .andExpect(erro(400, "REGRA_DE_NEGOCIO", painel));
    }

    @Test
    @DisplayName("A empresa dona vê o painel: 200, com e sem período")
    void dona200() throws Exception {
        when(dashboardEmpresaService.gerar(empresa.getId(), null, null)).thenReturn(painelVazio());
        when(dashboardEmpresaService.gerar(empresa.getId(), LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31)))
                .thenReturn(painelVazio());

        mockMvc.perform(get(painel).with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamentoTotal").value(150.00))
                .andExpect(jsonPath("$.totalAtendimentos").value(2))
                .andExpect(jsonPath("$.ticketMedio").value(75.00));
        mockMvc.perform(get(painel).param("dataInicio", "2030-01-01").param("dataFim", "2030-01-31")
                        .with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taxaConclusao").value(100.0));
    }
}
