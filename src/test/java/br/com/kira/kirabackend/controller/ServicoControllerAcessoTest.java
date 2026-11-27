package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.dto.request.ServicoRequest;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.ServicoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O AcessoEmpresa é o real: o dono é conferido de verdade antes de chegar ao serviço.
@WebMvcTest(controllers = ServicoController.class)
@Import({SecurityConfig.class, AcessoEmpresa.class})
class ServicoControllerAcessoTest extends BaseAcessoWeb {

    private static final String CORPO = "{\"nome\":\"Corte\",\"duracaoMinutos\":60,\"preco\":80.00}";

    @MockBean
    private ServicoService servicoService;

    private Empresa empresa;
    private String base;
    private UUID servicoId;
    private ServicoResponse servico;

    @BeforeEach
    void setUp() {
        empresa = novaEmpresa();
        base = "/api/empresas/" + empresa.getId() + "/servicos";
        servicoId = UUID.randomUUID();
        servico = new ServicoResponse(servicoId, "Corte", null, 60, new BigDecimal("80.00"), true,
                CategoriaServico.CABELO, null);
    }

    private static MockMultipartFile foto() {
        return new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("Sem token, cadastrar, editar, desativar e mexer na foto respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(base + "/" + servicoId).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + "/" + servicoId)).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(base + "/" + servicoId + "/foto").file(foto())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + "/" + servicoId + "/foto")).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicoService);
    }

    @Test
    @DisplayName("Sem token, a lista de serviços é pública (vitrine)")
    void listarPublico200() throws Exception {
        when(servicoService.listarPorEmpresa(empresa.getId())).thenReturn(List.of(servico));

        mockMvc.perform(get(base))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(servicoId.toString()))
                .andExpect(jsonPath("$[0].preco").value(80.00));
    }

    @Test
    @DisplayName("Lista de estúdio inexistente: 404")
    void listarEstudioInexistente404() throws Exception {
        when(servicoService.listarPorEmpresa(empresa.getId()))
                .thenThrow(new RecursoNaoEncontradoException("Empresa não encontrada"));

        mockMvc.perform(get(base)).andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", base));
    }

    @Test
    @DisplayName("Cadastrar: 201 com o serviço criado pela empresa dona")
    void cadastrar201() throws Exception {
        when(servicoService.cadastrar(eq(empresa.getId()), any(ServicoRequest.class))).thenReturn(servico);

        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(servicoId.toString()))
                .andExpect(jsonPath("$.nome").value("Corte"))
                .andExpect(jsonPath("$.categoria").value("CABELO"));
    }

    @Test
    @DisplayName("Outra empresa cadastrando, editando ou desativando: 403 sem chegar ao serviço")
    void outraEmpresa403() throws Exception {
        Empresa outra = novaEmpresa();
        mockMvc.perform(post(base).with(token(outra)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(403, "ACESSO_NEGADO", base));
        mockMvc.perform(put(base + "/" + servicoId).with(token(outra))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + servicoId));
        mockMvc.perform(delete(base + "/" + servicoId).with(token(outra)))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + servicoId));
        mockMvc.perform(multipart(base + "/" + servicoId + "/foto").file(foto()).with(token(outra)))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + servicoId + "/foto"));
        mockMvc.perform(delete(base + "/" + servicoId + "/foto").with(token(outra)))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + servicoId + "/foto"));
        verifyNoInteractions(servicoService);
    }

    @Test
    @DisplayName("Cliente cadastrando serviço: 403")
    void cliente403() throws Exception {
        Cliente cliente = novaCliente();
        mockMvc.perform(post(base).with(token(cliente)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(403, "ACESSO_NEGADO", base));
        verifyNoInteractions(servicoService);
    }

    @Test
    @DisplayName("Corpo inválido: 400 de validação com os campos")
    void corpoInvalido400() throws Exception {
        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\" \",\"duracaoMinutos\":0,\"preco\":-1}"))
                .andExpect(erro(400, "VALIDACAO", base))
                .andExpect(jsonPath("$.erros.nome").value("Nome é obrigatório"))
                .andExpect(jsonPath("$.erros.duracaoMinutos").value("Duração deve ser maior que zero"))
                .andExpect(jsonPath("$.erros.preco").value("Preço deve ser maior que zero"));
        mockMvc.perform(put(base + "/" + servicoId).with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(erro(400, "VALIDACAO", base + "/" + servicoId))
                .andExpect(jsonPath("$.erros.duracaoMinutos").value("Duração é obrigatória"))
                .andExpect(jsonPath("$.erros.preco").value("Preço é obrigatório"));
        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Corte\",\"duracaoMinutos\":60,\"preco\":80,\"categoria\":\"NAO_EXISTE\"}"))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", base));
        verifyNoInteractions(servicoService);
    }

    @Test
    @DisplayName("Editar serviço inexistente: 404")
    void editarInexistente404() throws Exception {
        when(servicoService.atualizar(eq(empresa.getId()), eq(servicoId), any(ServicoRequest.class)))
                .thenThrow(new RecursoNaoEncontradoException("Serviço não encontrado"));

        mockMvc.perform(put(base + "/" + servicoId).with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", base + "/" + servicoId))
                .andExpect(jsonPath("$.mensagem").value("Serviço não encontrado"));
    }

    @Test
    @DisplayName("Editar: 200 com o serviço atualizado")
    void editar200() throws Exception {
        when(servicoService.atualizar(eq(empresa.getId()), eq(servicoId), any(ServicoRequest.class))).thenReturn(servico);

        mockMvc.perform(put(base + "/" + servicoId).with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracaoMinutos").value(60));
    }

    @Test
    @DisplayName("Desativar: 204 sem corpo")
    void desativar204() throws Exception {
        mockMvc.perform(delete(base + "/" + servicoId).with(token(empresa)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(servicoService).desativar(empresa.getId(), servicoId);
    }

    @Test
    @DisplayName("Foto: 200 com o serviço; sem o arquivo, 400 de requisição inválida")
    void fotoDoCadastro() throws Exception {
        when(servicoService.definirFoto(eq(empresa.getId()), eq(servicoId), any())).thenReturn(servico);
        String caminho = base + "/" + servicoId + "/foto";

        mockMvc.perform(multipart(caminho).file(foto()).with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(servicoId.toString()));
        mockMvc.perform(multipart(caminho).with(token(empresa)))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", caminho));
    }

    @Test
    @DisplayName("Remover foto: 204")
    void removerFoto204() throws Exception {
        mockMvc.perform(delete(base + "/" + servicoId + "/foto").with(token(empresa)))
                .andExpect(status().isNoContent());
        verify(servicoService).removerFoto(empresa.getId(), servicoId);
    }
}
