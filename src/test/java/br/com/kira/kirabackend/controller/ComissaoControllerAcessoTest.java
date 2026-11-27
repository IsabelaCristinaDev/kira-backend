package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.dto.response.ComissaoResponse;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.ComissaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O AcessoEmpresa é o real: a funcionária é buscada e a empresa dela comparada com a conta logada.
@WebMvcTest(controllers = ComissaoController.class)
@Import({SecurityConfig.class, AcessoEmpresa.class})
class ComissaoControllerAcessoTest extends BaseAcessoWeb {

    @MockBean
    private ComissaoService comissaoService;

    private Empresa empresa;
    private Funcionaria funcionaria;
    private String lista;
    private String total;

    @BeforeEach
    void setUp() {
        empresa = novaEmpresa();
        funcionaria = funcionariaDa(empresa);
        lista = "/api/comissoes/funcionaria/" + funcionaria.getId();
        total = lista + "/total";
    }

    @Test
    @DisplayName("Sem token, a lista e o total de comissões respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(get(lista)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(total)).andExpect(status().isUnauthorized());
        verifyNoInteractions(comissaoService);
    }

    @Test
    @DisplayName("Outra empresa ou cliente: 403 na lista e no total")
    void outraConta403() throws Exception {
        Empresa outra = novaEmpresa();
        mockMvc.perform(get(lista).with(token(outra))).andExpect(erro(403, "ACESSO_NEGADO", lista));
        mockMvc.perform(get(total).with(token(outra))).andExpect(erro(403, "ACESSO_NEGADO", total));
        mockMvc.perform(get(lista).with(token(novaCliente()))).andExpect(erro(403, "ACESSO_NEGADO", lista));
        verifyNoInteractions(comissaoService);
    }

    @Test
    @DisplayName("Funcionária inexistente: 404")
    void funcionariaInexistente404() throws Exception {
        String caminho = "/api/comissoes/funcionaria/" + UUID.randomUUID() + "/total";
        mockMvc.perform(get(caminho).with(token(empresa))).andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", caminho));
        verifyNoInteractions(comissaoService);
    }

    @Test
    @DisplayName("Id que não é UUID: 400 de requisição inválida")
    void idInvalido400() throws Exception {
        String caminho = "/api/comissoes/funcionaria/123";
        mockMvc.perform(get(caminho).with(token(empresa))).andExpect(erro(400, "REQUISICAO_INVALIDA", caminho));
    }

    @Test
    @DisplayName("A empresa dona vê a lista e o total: 200")
    void dona200() throws Exception {
        ComissaoResponse comissao = new ComissaoResponse(UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 14, 0), "Ana", "Corte", new BigDecimal("80.00"),
                new BigDecimal("30.00"), new BigDecimal("24.00"), LocalDateTime.of(2030, 1, 10, 15, 0));
        when(comissaoService.listarPorFuncionaria(funcionaria.getId())).thenReturn(List.of(comissao));
        when(comissaoService.totalComissaoFuncionaria(funcionaria.getId())).thenReturn(new BigDecimal("24.00"));

        mockMvc.perform(get(lista).with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(comissao.id().toString()))
                .andExpect(jsonPath("$[0].valorComissao").value(24.00));
        mockMvc.perform(get(total).with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(24.00));
    }
}
