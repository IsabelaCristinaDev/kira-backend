package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import br.com.kira.kirabackend.dto.request.AvaliacaoRequest;
import br.com.kira.kirabackend.dto.response.AvaliacaoResponse;
import br.com.kira.kirabackend.exception.AcessoNegadoException;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.service.AvaliacaoService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Quem participou do atendimento e quem pode ver as avaliações de uma cliente é conferido no
// AvaliacaoService (coberto em AvaliacaoServiceTest). Aqui: login, id logado repassado e erros.
@WebMvcTest(controllers = AvaliacaoController.class)
@Import(SecurityConfig.class)
class AvaliacaoControllerAcessoTest extends BaseAcessoWeb {

    private static final String BASE = "/api/avaliacoes";

    @MockBean
    private AvaliacaoService avaliacaoService;

    private Cliente cliente;
    private Empresa empresa;
    private UUID agendamentoId;
    private AvaliacaoResponse avaliacao;

    @BeforeEach
    void setUp() {
        cliente = novaCliente();
        empresa = novaEmpresa();
        agendamentoId = UUID.randomUUID();
        avaliacao = new AvaliacaoResponse(UUID.randomUUID(), agendamentoId, cliente.getId(), "Ana", empresa.getId(),
                "Estúdio", TipoAvaliador.CLIENTE, 5, "Ótimo", LocalDateTime.of(2030, 1, 10, 16, 0));
    }

    private String corpo(int nota) {
        return "{\"agendamentoId\":\"%s\",\"nota\":%d,\"comentario\":\"Ótimo\"}".formatted(agendamentoId, nota);
    }

    @Test
    @DisplayName("Sem token, avaliar e ver as avaliações da pessoa respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(post(BASE + "/cliente").contentType(MediaType.APPLICATION_JSON).content(corpo(5)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/empresa").contentType(MediaType.APPLICATION_JSON).content(corpo(5)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/minhas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/cliente/" + cliente.getId())).andExpect(status().isUnauthorized());
        verifyNoInteractions(avaliacaoService);
    }

    @Test
    @DisplayName("Sem token, as avaliações e a média do estúdio continuam públicas")
    void avaliacoesDoEstudioPublicas() throws Exception {
        when(avaliacaoService.listarAvaliacoesEmpresa(empresa.getId())).thenReturn(List.of(avaliacao));
        when(avaliacaoService.mediaAvaliacaoEmpresa(empresa.getId())).thenReturn(4.5);

        mockMvc.perform(get(BASE + "/empresa/" + empresa.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nota").value(5));
        mockMvc.perform(get(BASE + "/empresa/" + empresa.getId() + "/media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(4.5));
    }

    @Test
    @DisplayName("Média de estúdio inexistente: 404")
    void mediaEstudioInexistente404() throws Exception {
        UUID id = UUID.randomUUID();
        when(avaliacaoService.mediaAvaliacaoEmpresa(id)).thenThrow(new RecursoNaoEncontradoException("Empresa não encontrada"));

        String caminho = BASE + "/empresa/" + id + "/media";
        mockMvc.perform(get(caminho)).andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", caminho));
    }

    @Test
    @DisplayName("Avaliar como cliente: 201 com a avaliação, em nome da conta logada")
    void avaliarComoCliente201() throws Exception {
        when(avaliacaoService.avaliarComoCliente(eq(cliente.getId()), any(AvaliacaoRequest.class))).thenReturn(avaliacao);

        mockMvc.perform(post(BASE + "/cliente").with(token(cliente))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(avaliacao.id().toString()))
                .andExpect(jsonPath("$.tipoAvaliador").value("CLIENTE"))
                .andExpect(jsonPath("$.nota").value(5));
        verify(avaliacaoService).avaliarComoCliente(cliente.getId(),
                new AvaliacaoRequest(agendamentoId, 5, "Ótimo"));
    }

    @Test
    @DisplayName("Nota fora de 1 a 5 ou sem agendamento: 400 de validação com os campos")
    void corpoInvalido400() throws Exception {
        String caminho = BASE + "/cliente";
        mockMvc.perform(post(caminho).with(token(cliente)).contentType(MediaType.APPLICATION_JSON).content(corpo(6)))
                .andExpect(erro(400, "VALIDACAO", caminho))
                .andExpect(jsonPath("$.erros.nota").value("Nota máxima é 5"));
        mockMvc.perform(post(caminho).with(token(cliente)).contentType(MediaType.APPLICATION_JSON).content(corpo(0)))
                .andExpect(jsonPath("$.erros.nota").value("Nota mínima é 1"));
        mockMvc.perform(post(BASE + "/empresa").with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(erro(400, "VALIDACAO", BASE + "/empresa"))
                .andExpect(jsonPath("$.erros.agendamentoId").value("Agendamento é obrigatório"))
                .andExpect(jsonPath("$.erros.nota").value("Nota é obrigatória"));
        verifyNoInteractions(avaliacaoService);
    }

    @Test
    @DisplayName("Empresa avaliando pela rota da cliente: 403")
    void empresaNaRotaDaCliente403() throws Exception {
        when(avaliacaoService.avaliarComoCliente(eq(empresa.getId()), any(AvaliacaoRequest.class)))
                .thenThrow(new AcessoNegadoException("Você não participou deste atendimento"));

        mockMvc.perform(post(BASE + "/cliente").with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(5)))
                .andExpect(erro(403, "ACESSO_NEGADO", BASE + "/cliente"));
    }

    @Test
    @DisplayName("Cliente avaliando pela rota da empresa: 403")
    void clienteNaRotaDaEmpresa403() throws Exception {
        when(avaliacaoService.avaliarComoEmpresa(eq(cliente.getId()), any(AvaliacaoRequest.class)))
                .thenThrow(new AcessoNegadoException("Este atendimento não pertence ao seu estabelecimento"));

        mockMvc.perform(post(BASE + "/empresa").with(token(cliente))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(5)))
                .andExpect(erro(403, "ACESSO_NEGADO", BASE + "/empresa"));
    }

    @Test
    @DisplayName("Avaliar agendamento inexistente: 404")
    void agendamentoInexistente404() throws Exception {
        when(avaliacaoService.avaliarComoEmpresa(eq(empresa.getId()), any(AvaliacaoRequest.class)))
                .thenThrow(new RecursoNaoEncontradoException("Agendamento não encontrado"));

        mockMvc.perform(post(BASE + "/empresa").with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(4)))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", BASE + "/empresa"));
    }

    @Test
    @DisplayName("Avaliar duas vezes: 400 de regra de negócio")
    void avaliarDuasVezes400() throws Exception {
        when(avaliacaoService.avaliarComoCliente(eq(cliente.getId()), any(AvaliacaoRequest.class)))
                .thenThrow(new RegraDeNegocioException("Você já avaliou este atendimento"));

        mockMvc.perform(post(BASE + "/cliente").with(token(cliente))
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(5)))
                .andExpect(erro(400, "REGRA_DE_NEGOCIO", BASE + "/cliente"))
                .andExpect(jsonPath("$.mensagem").value("Você já avaliou este atendimento"));
    }

    @Test
    @DisplayName("Minhas avaliações: 200 com as da conta logada")
    void minhas200() throws Exception {
        when(avaliacaoService.listarMinhasAvaliacoes(cliente)).thenReturn(List.of(avaliacao));

        mockMvc.perform(get(BASE + "/minhas").with(token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clienteId").value(cliente.getId().toString()));
    }

    @Test
    @DisplayName("Avaliações de outra cliente: 403")
    void avaliacoesDeOutraCliente403() throws Exception {
        Cliente outra = novaCliente();
        when(avaliacaoService.listarAvaliacoesCliente(outra.getId(), cliente))
                .thenThrow(new AcessoNegadoException("Você só pode ver as suas avaliações"));

        String caminho = BASE + "/cliente/" + outra.getId();
        mockMvc.perform(get(caminho).with(token(cliente))).andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }

    @Test
    @DisplayName("Avaliações da cliente vistas pelo estúdio que a atendeu: 200")
    void avaliacoesDaClientePeloEstudio200() throws Exception {
        when(avaliacaoService.listarAvaliacoesCliente(cliente.getId(), empresa)).thenReturn(List.of(avaliacao));

        mockMvc.perform(get(BASE + "/cliente/" + cliente.getId()).with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(avaliacao.id().toString()));
    }
}
