package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.dto.response.VerificacaoEmAnaliseResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.service.RevisaoVerificacaoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminVerificacaoController.class)
@Import(SecurityConfig.class)
class AdminVerificacaoControllerAcessoTest extends BaseAcessoWeb {

    private static final String BASE = "/api/admin/verificacoes";

    @MockBean
    private RevisaoVerificacaoService revisaoService;

    private static Admin novoAdmin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setNome("Admin");
        admin.setEmail("admin-" + admin.getId() + "@kira.test");
        admin.setAtivo(true);
        return admin;
    }

    @Test
    @DisplayName("Sem token: 401 nas três rotas")
    void semToken401() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get(BASE + "/em-analise")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + id + "/aprovar")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + id + "/reprovar")).andExpect(status().isUnauthorized());
        verifyNoInteractions(revisaoService);
    }

    @Test
    @DisplayName("Cliente e empresa: 403 sem chegar ao serviço")
    void naoAdmin403() throws Exception {
        UUID id = UUID.randomUUID();
        for (var usuario : List.of(novaCliente(), novaEmpresa())) {
            mockMvc.perform(get(BASE + "/em-analise").with(token(usuario))).andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/" + id + "/aprovar").with(token(usuario))).andExpect(status().isForbidden());
            mockMvc.perform(post(BASE + "/" + id + "/reprovar").with(token(usuario))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(revisaoService);
    }

    @Test
    @DisplayName("Admin lista as verificações em análise")
    void adminLista() throws Exception {
        UUID id = UUID.randomUUID();
        when(revisaoService.listarEmAnalise()).thenReturn(List.of(new VerificacaoEmAnaliseResponse(
                id, UUID.randomUUID(), "Ana", TipoUsuario.CLIENTE, TipoDocumentoIdentidade.CNH,
                List.of("LOW_FACE_SIMILARITY"), new BigDecimal("0.6100"), new BigDecimal("0.9000"),
                "req-1", LocalDateTime.of(2026, 11, 28, 10, 0))));

        mockMvc.perform(get(BASE + "/em-analise").with(token(novoAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].nome").value("Ana"))
                .andExpect(jsonPath("$[0].motivos[0]").value("LOW_FACE_SIMILARITY"))
                .andExpect(jsonPath("$[0].cpf").doesNotExist());
    }

    @Test
    @DisplayName("Admin aprova e reprova: 204, com o admin logado como revisor")
    void adminDecide() throws Exception {
        Admin admin = novoAdmin();
        UUID id = UUID.randomUUID();

        mockMvc.perform(post(BASE + "/" + id + "/aprovar").with(token(admin))).andExpect(status().isNoContent());
        mockMvc.perform(post(BASE + "/" + id + "/reprovar").with(token(admin))).andExpect(status().isNoContent());

        verify(revisaoService).aprovar(eq(id), eq(admin));
        verify(revisaoService).reprovar(eq(id), eq(admin));
    }

    @Test
    @DisplayName("Verificação fora de análise: 400 com a mensagem do serviço")
    void foraDeAnalise400() throws Exception {
        Admin admin = novoAdmin();
        UUID id = UUID.randomUUID();
        doThrow(new RegraDeNegocioException("Esta verificação não está em análise"))
                .when(revisaoService).aprovar(id, admin);

        mockMvc.perform(post(BASE + "/" + id + "/aprovar").with(token(admin)))
                .andExpect(erro(400, "REGRA_DE_NEGOCIO", BASE + "/" + id + "/aprovar"));
    }
}
