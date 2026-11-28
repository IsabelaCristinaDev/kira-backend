package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.request.BuscaEstudioFiltro;
import br.com.kira.kirabackend.dto.response.EstudioResponse;
import br.com.kira.kirabackend.dto.response.FuncionariaPublicaResponse;
import br.com.kira.kirabackend.dto.response.PaginaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.service.AvaliacaoService;
import br.com.kira.kirabackend.service.BuscaEstudioService;
import br.com.kira.kirabackend.service.EstudioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A vitrine é pública (GET /api/studios/**): sem token responde 200, e com login de cliente a busca recebe a conta.
@WebMvcTest(controllers = EstudioController.class)
@Import(SecurityConfig.class)
class EstudioControllerAcessoTest extends BaseAcessoWeb {

    private static final String BASE = "/api/studios";

    @MockBean
    private EstudioService estudioService;

    @MockBean
    private AvaliacaoService avaliacaoService;

    @MockBean
    private BuscaEstudioService buscaEstudioService;

    private UUID estudioId;
    private EstudioResponse estudio;

    @BeforeEach
    void setUp() {
        estudioId = UUID.randomUUID();
        estudio = new EstudioResponse(estudioId, "Estúdio", "Desc", null, "62999990000", null,
                TipoEstabelecimento.ESTABELECIMENTO, 4.5, null, null);
    }

    @Test
    @DisplayName("Sem token, a vitrine responde 200: lista, perfil, funcionárias e média")
    void vitrinePublica200() throws Exception {
        when(estudioService.listarTodos()).thenReturn(List.of(estudio));
        when(estudioService.buscarPorId(estudioId)).thenReturn(estudio);
        when(estudioService.listarFuncionarias(estudioId))
                .thenReturn(List.of(new FuncionariaPublicaResponse(UUID.randomUUID(), "Bia", null, "Corte")));
        when(avaliacaoService.mediaAvaliacaoEmpresa(estudioId)).thenReturn(4.5);

        mockMvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(estudioId.toString()));
        mockMvc.perform(get(BASE + "/" + estudioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Estúdio"))
                .andExpect(jsonPath("$.tipoEstabelecimento").value("ESTABELECIMENTO"));
        // A vitrine não traz comissão nem situação da funcionária
        mockMvc.perform(get(BASE + "/" + estudioId + "/funcionarias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Bia"))
                .andExpect(jsonPath("$[0].comissaoPercentual").doesNotExist())
                .andExpect(jsonPath("$[0].ativo").doesNotExist());
        mockMvc.perform(get(BASE + "/" + estudioId + "/media")).andExpect(status().isOk()).andExpect(jsonPath("$").value(4.5));
    }

    @Test
    @DisplayName("Estúdio inexistente: 404")
    void inexistente404() throws Exception {
        when(estudioService.buscarPorId(estudioId)).thenThrow(new RecursoNaoEncontradoException("Estúdio não encontrado"));

        mockMvc.perform(get(BASE + "/" + estudioId))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", BASE + "/" + estudioId))
                .andExpect(jsonPath("$.mensagem").value("Estúdio não encontrado"));
    }

    @Test
    @DisplayName("Parâmetros inválidos: 400 de requisição inválida")
    void parametrosInvalidos400() throws Exception {
        mockMvc.perform(get(BASE + "/busca")).andExpect(erro(400, "REQUISICAO_INVALIDA", BASE + "/busca"));
        mockMvc.perform(get(BASE + "/tipo").param("tipo", "SALAO_DE_FESTA"))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", BASE + "/tipo"));
        mockMvc.perform(get(BASE + "/pesquisa").param("pagina", "um"))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", BASE + "/pesquisa"));
        verifyNoInteractions(estudioService, buscaEstudioService);
    }

    @Test
    @DisplayName("Pesquisa sem login: 200, sem conta repassada ao serviço")
    void pesquisaSemLogin200() throws Exception {
        when(buscaEstudioService.buscar(any(BuscaEstudioFiltro.class), isNull(), eq(0), eq(20)))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(BASE + "/pesquisa").param("cidade", "Goiânia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(0))
                .andExpect(jsonPath("$.tamanho").value(20));
        verify(buscaEstudioService).buscar(
                new BuscaEstudioFiltro(null, "Goiânia", null, null, null, null, null, null, null), null, 0, 20);
    }

    @Test
    @DisplayName("Pesquisa com login de cliente: a conta vai ao serviço (para a distância)")
    void pesquisaComLogin200() throws Exception {
        Cliente cliente = novaCliente();
        when(buscaEstudioService.buscar(any(BuscaEstudioFiltro.class), eq(cliente), eq(1), eq(5)))
                .thenReturn(new PaginaResponse<>(List.of(), 1, 5, 0, 0));

        mockMvc.perform(get(BASE + "/pesquisa").param("pagina", "1").param("tamanho", "5").with(token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(1));
    }

    @Test
    @DisplayName("Busca por nome, tipo e cidade: 200")
    void buscas200() throws Exception {
        when(estudioService.buscarPorNome("Est")).thenReturn(List.of(estudio));
        when(estudioService.buscarPorTipo(TipoEstabelecimento.AUTONOMA)).thenReturn(List.of());
        when(estudioService.buscarPorCidade("Goiânia")).thenReturn(List.of(estudio));

        mockMvc.perform(get(BASE + "/busca").param("nome", "Est")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(BASE + "/tipo").param("tipo", "AUTONOMA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(BASE + "/cidade").param("cidade", "Goiânia")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(estudioId.toString()));
    }
}
