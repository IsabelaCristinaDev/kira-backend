package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.enums.FormaPagamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.request.AgendamentoRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.exception.AcessoNegadoException;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.service.AgendamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O dono do agendamento é conferido no AgendamentoService (coberto em AgendamentoServiceTest).
// Aqui: o login exigido, o id da conta logada repassado ao serviço e as respostas de erro.
@WebMvcTest(controllers = AgendamentoController.class)
@Import(SecurityConfig.class)
class AgendamentoControllerAcessoTest extends BaseAcessoWeb {

    private static final String BASE = "/api/agendamentos";

    @MockBean
    private AgendamentoService agendamentoService;

    private Cliente cliente;
    private Empresa empresa;
    private AgendamentoResponse agendamento;

    @BeforeEach
    void setUp() {
        cliente = novaCliente();
        empresa = novaEmpresa();
        LocalDateTime inicio = LocalDateTime.of(2030, 1, 10, 14, 0);
        agendamento = new AgendamentoResponse(UUID.randomUUID(), cliente.getId(), "Ana", empresa.getId(),
                "Estúdio", null, null, UUID.randomUUID(), "Corte", 60, inicio, inicio.plusHours(1),
                StatusAgendamento.PENDENTE, new BigDecimal("80.00"), FormaPagamento.PIX, null);
    }

    private String corpoCriar() {
        return """
                {"empresaId":"%s","servicoId":"%s","dataHoraInicio":"2030-01-10T14:00:00"}
                """.formatted(empresa.getId(), UUID.randomUUID());
    }

    @Test
    @DisplayName("Sem token, todas as rotas de agendamento respondem 401")
    void semToken401() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post(BASE + "/cliente/" + id).contentType(MediaType.APPLICATION_JSON).content(corpoCriar()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/cliente/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/cliente/" + id + "/historico")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/empresa/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE + "/" + id + "/cancelar")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE + "/" + id + "/confirmar")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE + "/" + id + "/concluir")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE + "/" + id + "/cancelar/empresa")).andExpect(status().isUnauthorized());
        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("Token recusado pelo provedor responde 401")
    void tokenInvalido401() throws Exception {
        mockMvc.perform(get(BASE + "/cliente/" + cliente.getId()).with(tokenInvalido()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("Criar: 201 com o agendamento e o id da conta logada repassado ao serviço")
    void criar201() throws Exception {
        when(agendamentoService.criar(eq(cliente.getId()), eq(cliente.getId()), any(AgendamentoRequest.class)))
                .thenReturn(agendamento);

        mockMvc.perform(post(BASE + "/cliente/" + cliente.getId()).with(token(cliente))
                        .contentType(MediaType.APPLICATION_JSON).content(corpoCriar()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(agendamento.id().toString()))
                .andExpect(jsonPath("$.clienteId").value(cliente.getId().toString()))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }

    @Test
    @DisplayName("Criar sem os campos obrigatórios: 400 de validação com os campos")
    void criarCorpoInvalido400() throws Exception {
        String caminho = BASE + "/cliente/" + cliente.getId();
        mockMvc.perform(post(caminho).with(token(cliente)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(erro(400, "VALIDACAO", caminho))
                .andExpect(jsonPath("$.erros.empresaId").value("Empresa é obrigatória"))
                .andExpect(jsonPath("$.erros.servicoId").value("Serviço é obrigatório"))
                .andExpect(jsonPath("$.erros.dataHoraInicio").value("Data e hora são obrigatórios"));
        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("Criar com JSON quebrado ou data inválida: 400 de requisição inválida")
    void criarJsonQuebrado400() throws Exception {
        String caminho = BASE + "/cliente/" + cliente.getId();
        mockMvc.perform(post(caminho).with(token(cliente)).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", caminho));
        mockMvc.perform(post(caminho).with(token(cliente)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empresaId\":\"%s\",\"servicoId\":\"%s\",\"dataHoraInicio\":\"amanhã\"}"
                                .formatted(empresa.getId(), UUID.randomUUID())))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", caminho));
        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("Id que não é UUID na rota: 400 de requisição inválida")
    void idInvalido400() throws Exception {
        mockMvc.perform(patch(BASE + "/abc/cancelar").with(token(cliente)))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", BASE + "/abc/cancelar"));
    }

    @Test
    @DisplayName("Criar em nome de outra cliente: o serviço recebe o id logado e a recusa vira 403")
    void criarParaOutraCliente403() throws Exception {
        UUID outraCliente = UUID.randomUUID();
        when(agendamentoService.criar(eq(outraCliente), eq(cliente.getId()), any(AgendamentoRequest.class)))
                .thenThrow(new AcessoNegadoException("Você só pode acessar os seus agendamentos"));

        String caminho = BASE + "/cliente/" + outraCliente;
        mockMvc.perform(post(caminho).with(token(cliente)).contentType(MediaType.APPLICATION_JSON).content(corpoCriar()))
                .andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }

    @Test
    @DisplayName("Listar da cliente: 200 com a lista, consultada com o id logado")
    void listarPorCliente200() throws Exception {
        when(agendamentoService.listarPorCliente(cliente.getId(), cliente.getId())).thenReturn(List.of(agendamento));

        mockMvc.perform(get(BASE + "/cliente/" + cliente.getId()).with(token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(agendamento.id().toString()));
    }

    @Test
    @DisplayName("Listar de outra cliente: 403")
    void listarDeOutraCliente403() throws Exception {
        UUID outraCliente = UUID.randomUUID();
        when(agendamentoService.listarPorCliente(outraCliente, cliente.getId()))
                .thenThrow(new AcessoNegadoException("Você só pode acessar os seus agendamentos"));

        String caminho = BASE + "/cliente/" + outraCliente;
        mockMvc.perform(get(caminho).with(token(cliente))).andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }

    @Test
    @DisplayName("Histórico de outra cliente: 403")
    void historicoDeOutraCliente403() throws Exception {
        UUID outraCliente = UUID.randomUUID();
        when(agendamentoService.historicoCliente(outraCliente, cliente.getId()))
                .thenThrow(new AcessoNegadoException("Você só pode acessar os seus agendamentos"));

        String caminho = BASE + "/cliente/" + outraCliente + "/historico";
        mockMvc.perform(get(caminho).with(token(cliente))).andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }

    @Test
    @DisplayName("Cliente na lista da empresa: 403")
    void clienteNaListaDaEmpresa403() throws Exception {
        when(agendamentoService.listarPorEmpresa(empresa.getId(), cliente.getId()))
                .thenThrow(new AcessoNegadoException("Você só pode acessar os dados do seu estabelecimento"));

        String caminho = BASE + "/empresa/" + empresa.getId();
        mockMvc.perform(get(caminho).with(token(cliente))).andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }

    @Test
    @DisplayName("Listar da empresa: 200 com a lista, consultada com o id logado")
    void listarPorEmpresa200() throws Exception {
        when(agendamentoService.listarPorEmpresa(empresa.getId(), empresa.getId())).thenReturn(List.of(agendamento));

        mockMvc.perform(get(BASE + "/empresa/" + empresa.getId()).with(token(empresa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].empresaId").value(empresa.getId().toString()));
    }

    @Test
    @DisplayName("Cliente confirmando atendimento: 403")
    void clienteConfirmando403() throws Exception {
        UUID id = agendamento.id();
        when(agendamentoService.confirmar(id, cliente.getId()))
                .thenThrow(new AcessoNegadoException("Este atendimento não pertence ao seu estabelecimento"));

        String caminho = BASE + "/" + id + "/confirmar";
        mockMvc.perform(patch(caminho).with(token(cliente))).andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }

    @Test
    @DisplayName("Empresa cancelando como cliente: 403")
    void empresaCancelandoComoCliente403() throws Exception {
        UUID id = agendamento.id();
        when(agendamentoService.cancelarPeloCliente(id, empresa.getId()))
                .thenThrow(new AcessoNegadoException("Você não participou deste atendimento"));

        String caminho = BASE + "/" + id + "/cancelar";
        mockMvc.perform(patch(caminho).with(token(empresa))).andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }

    @Test
    @DisplayName("Agendamento inexistente: 404")
    void inexistente404() throws Exception {
        UUID id = UUID.randomUUID();
        when(agendamentoService.cancelarPeloCliente(id, cliente.getId()))
                .thenThrow(new RecursoNaoEncontradoException("Agendamento não encontrado"));

        String caminho = BASE + "/" + id + "/cancelar";
        mockMvc.perform(patch(caminho).with(token(cliente)))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", caminho))
                .andExpect(jsonPath("$.mensagem").value("Agendamento não encontrado"));
    }

    @Test
    @DisplayName("Regra de negócio recusada: 400 com a mensagem do serviço")
    void regraDeNegocio400() throws Exception {
        UUID id = agendamento.id();
        when(agendamentoService.confirmar(id, empresa.getId()))
                .thenThrow(new RegraDeNegocioException("Não é possível confirmar este agendamento"));

        String caminho = BASE + "/" + id + "/confirmar";
        mockMvc.perform(patch(caminho).with(token(empresa)))
                .andExpect(erro(400, "REGRA_DE_NEGOCIO", caminho))
                .andExpect(jsonPath("$.mensagem").value("Não é possível confirmar este agendamento"));
    }

    @Test
    @DisplayName("Cancelar pela cliente: 200 com o agendamento")
    void cancelar200() throws Exception {
        AgendamentoResponse cancelado = new AgendamentoResponse(agendamento.id(), cliente.getId(), "Ana",
                empresa.getId(), "Estúdio", null, null, agendamento.servicoId(), "Corte", 60,
                agendamento.dataHoraInicio(), agendamento.dataHoraFim(), StatusAgendamento.CANCELADO,
                agendamento.valor(), FormaPagamento.PIX, null);
        when(agendamentoService.cancelarPeloCliente(agendamento.id(), cliente.getId())).thenReturn(cancelado);

        mockMvc.perform(patch(BASE + "/" + agendamento.id() + "/cancelar").with(token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    @DisplayName("Concluir sem corpo: 200 e a forma de pagamento vai nula ao serviço")
    void concluirSemCorpo200() throws Exception {
        when(agendamentoService.concluir(eq(agendamento.id()), eq(empresa.getId()), isNull())).thenReturn(agendamento);

        mockMvc.perform(patch(BASE + "/" + agendamento.id() + "/concluir").with(token(empresa)))
                .andExpect(status().isOk());
        verify(agendamentoService).concluir(agendamento.id(), empresa.getId(), null);
    }

    @Test
    @DisplayName("Concluir com a forma de pagamento: repassada ao serviço")
    void concluirComForma200() throws Exception {
        when(agendamentoService.concluir(agendamento.id(), empresa.getId(), FormaPagamento.DINHEIRO))
                .thenReturn(agendamento);

        mockMvc.perform(patch(BASE + "/" + agendamento.id() + "/concluir").with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"formaPagamento\":\"DINHEIRO\"}"))
                .andExpect(status().isOk());
        verify(agendamentoService).concluir(agendamento.id(), empresa.getId(), FormaPagamento.DINHEIRO);
    }

    @Test
    @DisplayName("Corrigir forma de pagamento sem o campo: 400 de validação")
    void formaPagamentoInvalida400() throws Exception {
        String caminho = BASE + "/" + agendamento.id() + "/forma-pagamento";
        mockMvc.perform(patch(caminho).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(erro(400, "VALIDACAO", caminho))
                .andExpect(jsonPath("$.erros.formaPagamento").value("Forma de pagamento é obrigatória"));
        mockMvc.perform(patch(caminho).with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formaPagamento\":\"CHEQUE_VOADOR\"}"))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", caminho));
        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("Corrigir forma de pagamento: 200 com o id logado")
    void corrigirFormaPagamento200() throws Exception {
        when(agendamentoService.corrigirFormaPagamento(agendamento.id(), empresa.getId(), FormaPagamento.PIX))
                .thenReturn(agendamento);

        mockMvc.perform(patch(BASE + "/" + agendamento.id() + "/forma-pagamento").with(token(empresa))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"formaPagamento\":\"PIX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formaPagamento").value("PIX"));
    }

    @Test
    @DisplayName("Reagendar sem a nova data: 400 de validação, nas duas rotas")
    void reagendarInvalido400() throws Exception {
        for (String quem : List.of("cliente", "empresa")) {
            String caminho = BASE + "/" + agendamento.id() + "/reagendar/" + quem;
            mockMvc.perform(patch(caminho).with(token(cliente)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(erro(400, "VALIDACAO", caminho))
                    .andExpect(jsonPath("$.erros.novaDataHoraInicio").value("Nova data e hora são obrigatórias"));
        }
        verifyNoInteractions(agendamentoService);
    }

    @Test
    @DisplayName("Cliente reagendando pela rota da empresa: 403")
    void clienteReagendandoComoEmpresa403() throws Exception {
        when(agendamentoService.reagendarPelaEmpresa(eq(agendamento.id()), eq(cliente.getId()), any()))
                .thenThrow(new AcessoNegadoException("Este atendimento não pertence ao seu estabelecimento"));

        String caminho = BASE + "/" + agendamento.id() + "/reagendar/empresa";
        mockMvc.perform(patch(caminho).with(token(cliente)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaDataHoraInicio\":\"2030-01-11T10:00:00\"}"))
                .andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }

    @Test
    @DisplayName("Reagendar pela cliente: 200 com o agendamento")
    void reagendarPeloCliente200() throws Exception {
        when(agendamentoService.reagendarPeloCliente(eq(agendamento.id()), eq(cliente.getId()), any()))
                .thenReturn(agendamento);

        mockMvc.perform(patch(BASE + "/" + agendamento.id() + "/reagendar/cliente").with(token(cliente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"novaDataHoraInicio\":\"2030-01-11T10:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agendamento.id().toString()));
    }

    @Test
    @DisplayName("Cancelar pela empresa de outro estabelecimento: 403")
    void cancelarPelaOutraEmpresa403() throws Exception {
        Empresa outra = novaEmpresa();
        when(agendamentoService.cancelarPelaEmpresa(agendamento.id(), outra.getId()))
                .thenThrow(new AcessoNegadoException("Este atendimento não pertence ao seu estabelecimento"));

        String caminho = BASE + "/" + agendamento.id() + "/cancelar/empresa";
        mockMvc.perform(patch(caminho).with(token(outra))).andExpect(erro(403, "ACESSO_NEGADO", caminho));
    }
}
