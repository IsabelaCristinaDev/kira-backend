package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.dto.request.FuncionariaRequest;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.FuncionariaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O AcessoEmpresa é o real: o dono é conferido de verdade antes de chegar ao serviço.
@WebMvcTest(controllers = FuncionariaController.class)
@Import({SecurityConfig.class, AcessoEmpresa.class})
class FuncionariaControllerAcessoTest extends BaseAcessoWeb {

    private static final String CORPO = "{\"nome\":\"Bia\",\"especialidades\":\"Corte\",\"comissaoPercentual\":30}";

    @MockBean
    private FuncionariaService funcionariaService;

    private Empresa empresa;
    private String base;
    private UUID funcionariaId;
    private FuncionariaResponse funcionaria;

    @BeforeEach
    void setUp() {
        empresa = novaEmpresa();
        base = "/api/empresas/" + empresa.getId() + "/funcionarias";
        funcionariaId = UUID.randomUUID();
        funcionaria = new FuncionariaResponse(funcionariaId, "Bia", null, "Corte", true, new BigDecimal("30.00"));
    }

    private static MockMultipartFile foto() {
        return new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("Sem token, cadastrar, editar, desativar e mexer na foto respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(base + "/" + funcionariaId).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + "/" + funcionariaId)).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(base + "/" + funcionariaId + "/foto").file(foto()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + "/" + funcionariaId + "/foto")).andExpect(status().isUnauthorized());
        verifyNoInteractions(funcionariaService);
    }

    /** Status e corpo da lista da área do estúdio para a requisição dada. */
    private MockHttpServletResponse listar(MockHttpServletRequestBuilder requisicao) throws Exception {
        when(funcionariaService.listarPorEmpresa(empresa.getId())).thenReturn(List.of(funcionaria));
        return mockMvc.perform(requisicao).andReturn().getResponse();
    }

    // A vitrine tem rota própria (/api/studios/{id}/funcionarias, com FuncionariaPublicaResponse)
    @Test
    @DisplayName("Sem login, a lista da área do estúdio responde 401")
    void listaDaAreaDoEstudioSemLogin() throws Exception {
        MockHttpServletResponse resposta = listar(get(base));

        assertEquals(401, resposta.getStatus());
        verifyNoInteractions(funcionariaService);
    }

    @Test
    @DisplayName("Outra empresa não lista a equipe do estúdio: 403")
    void listaDaAreaDoEstudioOutraConta() throws Exception {
        MockHttpServletResponse resposta = listar(get(base).with(token(novaEmpresa())));

        assertEquals(403, resposta.getStatus());
        verify(funcionariaService, never()).listarPorEmpresa(any());
    }

    @Test
    @DisplayName("A empresa dona lista a equipe com a comissão: 200")
    void listarDona200() throws Exception {
        MockHttpServletResponse resposta = listar(get(base).with(token(empresa)));

        assertTrue(resposta.getStatus() == 200, "status " + resposta.getStatus());
        assertTrue(resposta.getContentAsString().contains(funcionariaId.toString()));
    }

    @Test
    @DisplayName("Lista de estúdio inexistente: 404")
    void listarEstudioInexistente404() throws Exception {
        when(funcionariaService.listarPorEmpresa(empresa.getId()))
                .thenThrow(new RecursoNaoEncontradoException("Empresa não encontrada"));

        mockMvc.perform(get(base).with(token(empresa))).andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", base));
    }

    @Test
    @DisplayName("Cadastrar: 201 com a funcionária criada pela empresa dona")
    void cadastrar201() throws Exception {
        when(funcionariaService.cadastrar(eq(empresa.getId()), any(FuncionariaRequest.class))).thenReturn(funcionaria);

        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(funcionariaId.toString()))
                .andExpect(jsonPath("$.nome").value("Bia"))
                .andExpect(jsonPath("$.comissaoPercentual").value(30.00));
        verify(funcionariaService).cadastrar(empresa.getId(),
                new FuncionariaRequest("Bia", "Corte", new BigDecimal("30")));
    }

    @Test
    @DisplayName("Outra empresa ou cliente cadastrando, editando ou desativando: 403 sem chegar ao serviço")
    void outraConta403() throws Exception {
        Empresa outra = novaEmpresa();
        Cliente cliente = novaCliente();
        mockMvc.perform(post(base).with(token(outra)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(403, "ACESSO_NEGADO", base));
        mockMvc.perform(post(base).with(token(cliente)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(403, "ACESSO_NEGADO", base));
        mockMvc.perform(put(base + "/" + funcionariaId).with(token(outra))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + funcionariaId));
        mockMvc.perform(delete(base + "/" + funcionariaId).with(token(outra)))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + funcionariaId));
        mockMvc.perform(multipart(base + "/" + funcionariaId + "/foto").file(foto()).with(token(outra)))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + funcionariaId + "/foto"));
        mockMvc.perform(delete(base + "/" + funcionariaId + "/foto").with(token(outra)))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + funcionariaId + "/foto"));
        verifyNoInteractions(funcionariaService);
    }

    @Test
    @DisplayName("Corpo inválido: 400 de validação com os campos")
    void corpoInvalido400() throws Exception {
        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\",\"comissaoPercentual\":150}"))
                .andExpect(erro(400, "VALIDACAO", base))
                .andExpect(jsonPath("$.erros.nome").value("Nome é obrigatório"))
                .andExpect(jsonPath("$.erros.comissaoPercentual").value("Comissão deve ser de no máximo 100%"));
        mockMvc.perform(put(base + "/" + funcionariaId).with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Bia\",\"comissaoPercentual\":10.555}"))
                .andExpect(erro(400, "VALIDACAO", base + "/" + funcionariaId))
                .andExpect(jsonPath("$.erros.comissaoPercentual").value("Comissão deve ter no máximo 2 casas decimais"));
        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", base));
        verifyNoInteractions(funcionariaService);
    }

    @Test
    @DisplayName("Nome repetido: 400 de regra de negócio")
    void nomeRepetido400() throws Exception {
        when(funcionariaService.cadastrar(eq(empresa.getId()), any(FuncionariaRequest.class)))
                .thenThrow(new RegraDeNegocioException("Já existe uma funcionária ativa com esse nome"));

        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(400, "REGRA_DE_NEGOCIO", base));
    }

    @Test
    @DisplayName("Editar funcionária inexistente: 404")
    void editarInexistente404() throws Exception {
        when(funcionariaService.atualizar(eq(empresa.getId()), eq(funcionariaId), any(FuncionariaRequest.class)))
                .thenThrow(new RecursoNaoEncontradoException("Funcionária não encontrada"));

        mockMvc.perform(put(base + "/" + funcionariaId).with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", base + "/" + funcionariaId))
                .andExpect(jsonPath("$.mensagem").value("Funcionária não encontrada"));
    }

    @Test
    @DisplayName("Editar: 200; desativar: 204")
    void editarEDesativar() throws Exception {
        when(funcionariaService.atualizar(eq(empresa.getId()), eq(funcionariaId), any(FuncionariaRequest.class)))
                .thenReturn(funcionaria);

        mockMvc.perform(put(base + "/" + funcionariaId).with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.especialidades").value("Corte"));
        mockMvc.perform(delete(base + "/" + funcionariaId).with(token(empresa)))
                .andExpect(status().isNoContent());
        verify(funcionariaService).desativar(empresa.getId(), funcionariaId);
    }

    @Test
    @DisplayName("Foto: 200 com a funcionária; remover: 204; sem o arquivo: 400")
    void fotoDoCadastro() throws Exception {
        when(funcionariaService.definirFoto(eq(empresa.getId()), eq(funcionariaId), any())).thenReturn(funcionaria);
        String caminho = base + "/" + funcionariaId + "/foto";

        mockMvc.perform(multipart(caminho).file(foto()).with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(funcionariaId.toString()));
        mockMvc.perform(multipart(caminho).with(token(empresa)))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", caminho));
        mockMvc.perform(delete(caminho).with(token(empresa))).andExpect(status().isNoContent());
        verify(funcionariaService).removerFoto(empresa.getId(), funcionariaId);
    }
}
