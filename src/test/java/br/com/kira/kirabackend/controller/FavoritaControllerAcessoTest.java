package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.response.EstudioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.service.FavoritaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// As favoritas não têm id de conta na rota: o serviço recebe sempre a conta logada.
@WebMvcTest(controllers = FavoritaController.class)
@Import(SecurityConfig.class)
class FavoritaControllerAcessoTest extends BaseAcessoWeb {

    private static final String BASE = "/api/favoritas";

    @MockBean
    private FavoritaService favoritaService;

    private Cliente cliente;
    private UUID estudioId;

    @BeforeEach
    void setUp() {
        cliente = novaCliente();
        estudioId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Sem token, listar, salvar e remover respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE + "/" + estudioId)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/" + estudioId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(favoritaService);
    }

    @Test
    @DisplayName("Listar: 200 com as favoritas da cliente logada")
    void listar200() throws Exception {
        EstudioResponse estudio = new EstudioResponse(estudioId, "Estúdio", null, null, null, null,
                TipoEstabelecimento.AUTONOMA, null, null, null);
        when(favoritaService.listar(cliente)).thenReturn(List.of(estudio));

        mockMvc.perform(get(BASE).with(token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(estudioId.toString()));
    }

    @Test
    @DisplayName("Salvar e remover: 204, sempre com a conta logada")
    void salvarERemover204() throws Exception {
        mockMvc.perform(put(BASE + "/" + estudioId).with(token(cliente))).andExpect(status().isNoContent());
        mockMvc.perform(delete(BASE + "/" + estudioId).with(token(cliente))).andExpect(status().isNoContent());
        verify(favoritaService).salvar(cliente, estudioId);
        verify(favoritaService).remover(cliente, estudioId);
    }

    @Test
    @DisplayName("Conta de empresa: 400 (só clientes têm favoritas)")
    void empresa400() throws Exception {
        Empresa empresa = novaEmpresa();
        when(favoritaService.listar(empresa)).thenThrow(new RegraDeNegocioException("Só clientes têm favoritas"));

        mockMvc.perform(get(BASE).with(token(empresa)))
                .andExpect(erro(400, "REGRA_DE_NEGOCIO", BASE))
                .andExpect(jsonPath("$.mensagem").value("Só clientes têm favoritas"));
    }

    @Test
    @DisplayName("Salvar estúdio inexistente: 404")
    void estudioInexistente404() throws Exception {
        doThrow(new RecursoNaoEncontradoException("Estúdio não encontrado"))
                .when(favoritaService).salvar(cliente, estudioId);

        mockMvc.perform(put(BASE + "/" + estudioId).with(token(cliente)))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", BASE + "/" + estudioId));
    }

    @Test
    @DisplayName("Id do estúdio que não é UUID: 400 de requisição inválida")
    void idInvalido400() throws Exception {
        mockMvc.perform(put(BASE + "/x").with(token(cliente))).andExpect(erro(400, "REQUISICAO_INVALIDA", BASE + "/x"));
        verifyNoInteractions(favoritaService);
    }
}
