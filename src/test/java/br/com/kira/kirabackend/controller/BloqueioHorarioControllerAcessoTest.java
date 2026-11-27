package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.dto.request.BloqueioHorarioRequest;
import br.com.kira.kirabackend.dto.response.BloqueioHorarioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.security.AcessoEmpresa;
import br.com.kira.kirabackend.service.BloqueioHorarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O AcessoEmpresa é o real: a funcionária é buscada e a empresa dela comparada com a conta logada.
@WebMvcTest(controllers = BloqueioHorarioController.class)
@Import({SecurityConfig.class, AcessoEmpresa.class})
class BloqueioHorarioControllerAcessoTest extends BaseAcessoWeb {

    private static final String CORPO =
            "{\"dataHoraInicio\":\"2030-01-10T12:00:00\",\"dataHoraFim\":\"2030-01-10T13:00:00\",\"motivo\":\"Almoço\"}";

    @MockBean
    private BloqueioHorarioService bloqueioHorarioService;

    private Empresa empresa;
    private Funcionaria funcionaria;
    private String base;
    private BloqueioHorarioResponse bloqueio;

    @BeforeEach
    void setUp() {
        empresa = novaEmpresa();
        funcionaria = funcionariaDa(empresa);
        base = "/api/funcionarias/" + funcionaria.getId() + "/bloqueios";
        bloqueio = new BloqueioHorarioResponse(UUID.randomUUID(), funcionaria.getId(), "Bia",
                LocalDateTime.of(2030, 1, 10, 12, 0), LocalDateTime.of(2030, 1, 10, 13, 0), "Almoço");
    }

    @Test
    @DisplayName("Sem token, cadastrar, listar e remover respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(base)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + "/" + bloqueio.id())).andExpect(status().isUnauthorized());
        verifyNoInteractions(bloqueioHorarioService);
    }

    @Test
    @DisplayName("Cadastrar: 201 com o bloqueio")
    void cadastrar201() throws Exception {
        when(bloqueioHorarioService.cadastrar(eq(funcionaria.getId()), any(BloqueioHorarioRequest.class)))
                .thenReturn(bloqueio);

        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bloqueio.id().toString()))
                .andExpect(jsonPath("$.funcionariaId").value(funcionaria.getId().toString()))
                .andExpect(jsonPath("$.motivo").value("Almoço"));
    }

    @Test
    @DisplayName("Funcionária de outra empresa: 403 em cadastrar, listar e remover")
    void funcionariaDeOutraEmpresa403() throws Exception {
        Empresa outra = novaEmpresa();
        mockMvc.perform(post(base).with(token(outra)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(403, "ACESSO_NEGADO", base));
        mockMvc.perform(get(base).with(token(outra))).andExpect(erro(403, "ACESSO_NEGADO", base));
        mockMvc.perform(delete(base + "/" + bloqueio.id()).with(token(outra)))
                .andExpect(erro(403, "ACESSO_NEGADO", base + "/" + bloqueio.id()));
        verifyNoInteractions(bloqueioHorarioService);
    }

    @Test
    @DisplayName("Cliente na agenda de bloqueios: 403")
    void cliente403() throws Exception {
        mockMvc.perform(get(base).with(token(novaCliente()))).andExpect(erro(403, "ACESSO_NEGADO", base));
        verifyNoInteractions(bloqueioHorarioService);
    }

    @Test
    @DisplayName("Funcionária inexistente: 404")
    void funcionariaInexistente404() throws Exception {
        String caminho = "/api/funcionarias/" + UUID.randomUUID() + "/bloqueios";
        mockMvc.perform(get(caminho).with(token(empresa)))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", caminho))
                .andExpect(jsonPath("$.mensagem").value("Funcionária não encontrada"));
        verifyNoInteractions(bloqueioHorarioService);
    }

    @Test
    @DisplayName("Corpo sem as datas: 400 de validação com os campos")
    void corpoInvalido400() throws Exception {
        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(erro(400, "VALIDACAO", base))
                .andExpect(jsonPath("$.erros.dataHoraInicio").value("Data e hora de início são obrigatórios"))
                .andExpect(jsonPath("$.erros.dataHoraFim").value("Data e hora de fim são obrigatórios"));
        verifyNoInteractions(bloqueioHorarioService);
    }

    @Test
    @DisplayName("Fim antes do início: 400 de regra de negócio")
    void fimAntesDoInicio400() throws Exception {
        when(bloqueioHorarioService.cadastrar(eq(funcionaria.getId()), any(BloqueioHorarioRequest.class)))
                .thenThrow(new RegraDeNegocioException("Data de fim deve ser após data de início"));

        mockMvc.perform(post(base).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(erro(400, "REGRA_DE_NEGOCIO", base));
    }

    @Test
    @DisplayName("Listar: 200 com os bloqueios da funcionária")
    void listar200() throws Exception {
        when(bloqueioHorarioService.listarPorFuncionaria(funcionaria.getId())).thenReturn(List.of(bloqueio));

        mockMvc.perform(get(base).with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(bloqueio.id().toString()));
    }

    @Test
    @DisplayName("Remover: 204; bloqueio inexistente: 404")
    void remover() throws Exception {
        mockMvc.perform(delete(base + "/" + bloqueio.id()).with(token(empresa))).andExpect(status().isNoContent());
        verify(bloqueioHorarioService).remover(funcionaria.getId(), bloqueio.id());

        UUID inexistente = UUID.randomUUID();
        doThrow(new RecursoNaoEncontradoException("Bloqueio não encontrado"))
                .when(bloqueioHorarioService).remover(funcionaria.getId(), inexistente);
        mockMvc.perform(delete(base + "/" + inexistente).with(token(empresa)))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", base + "/" + inexistente));
    }
}
