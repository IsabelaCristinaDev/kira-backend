package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.config.SecurityConfig;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.enums.FormaPagamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.dto.response.CodigoChegadaResponse;
import br.com.kira.kirabackend.exception.AcessoNegadoException;
import br.com.kira.kirabackend.exception.CodigoChegadaException;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.service.ChegadaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O dono do agendamento é conferido no ChegadaService (coberto em ChegadaServiceTest).
@WebMvcTest(controllers = ChegadaController.class)
@Import(SecurityConfig.class)
class ChegadaControllerAcessoTest extends BaseAcessoWeb {

    @MockBean
    private ChegadaService chegadaService;

    private Cliente cliente;
    private Empresa empresa;
    private UUID agendamentoId;
    private String token;
    private String validar;

    @BeforeEach
    void setUp() {
        cliente = novaCliente();
        empresa = novaEmpresa();
        agendamentoId = UUID.randomUUID();
        token = "/api/agendamentos/" + agendamentoId + "/token-chegada";
        validar = "/api/agendamentos/" + agendamentoId + "/chegada/validar";
    }

    @Test
    @DisplayName("Sem token, gerar, ver e validar o código respondem 401")
    void semToken401() throws Exception {
        mockMvc.perform(post(token)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(token)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(validar).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"123456\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(chegadaService);
    }

    @Test
    @DisplayName("Gerar: 200 com o código e a validade, para a cliente logada")
    void gerar200() throws Exception {
        OffsetDateTime expira = OffsetDateTime.of(2030, 1, 10, 14, 30, 0, 0, ZoneOffset.ofHours(-3));
        when(chegadaService.gerar(agendamentoId, cliente.getId())).thenReturn(new CodigoChegadaResponse("482913", expira));

        mockMvc.perform(post(token).with(token(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("482913"))
                .andExpect(jsonPath("$.expiraEm").exists());
    }

    @Test
    @DisplayName("Gerar para agendamento de outra conta: 403")
    void gerarDeOutraConta403() throws Exception {
        when(chegadaService.gerar(agendamentoId, empresa.getId()))
                .thenThrow(new AcessoNegadoException("Você só pode ver os seus próprios agendamentos"));

        mockMvc.perform(post(token).with(token(empresa))).andExpect(erro(403, "ACESSO_NEGADO", token));
    }

    @Test
    @DisplayName("Ver o código sem nenhum ativo: 404")
    void semCodigoAtivo404() throws Exception {
        when(chegadaService.ativo(agendamentoId, cliente.getId()))
                .thenThrow(new RecursoNaoEncontradoException("Nenhum código de chegada ativo"));

        mockMvc.perform(get(token).with(token(cliente)))
                .andExpect(erro(404, "RECURSO_NAO_ENCONTRADO", token))
                .andExpect(jsonPath("$.mensagem").value("Nenhum código de chegada ativo"));
    }

    @Test
    @DisplayName("Validar sem código ou com formato errado: 400 de validação")
    void validarCorpoInvalido400() throws Exception {
        mockMvc.perform(post(validar).with(token(empresa)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(erro(400, "VALIDACAO", validar))
                .andExpect(jsonPath("$.erros.codigo").exists());
        mockMvc.perform(post(validar).with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"12ab\"}"))
                .andExpect(erro(400, "VALIDACAO", validar))
                .andExpect(jsonPath("$.erros.codigo").value("O código tem 6 números"));
        verifyNoInteractions(chegadaService);
    }

    @Test
    @DisplayName("Validar sem corpo: 400 de requisição inválida")
    void validarSemCorpo400() throws Exception {
        mockMvc.perform(post(validar).with(token(empresa)).contentType(MediaType.APPLICATION_JSON))
                .andExpect(erro(400, "REQUISICAO_INVALIDA", validar));
        verifyNoInteractions(chegadaService);
    }

    @Test
    @DisplayName("Cliente validando o próprio código: 403")
    void clienteValidando403() throws Exception {
        when(chegadaService.validar(agendamentoId, cliente.getId(), "123456"))
                .thenThrow(new AcessoNegadoException("Este atendimento não pertence ao seu estabelecimento"));

        mockMvc.perform(post(validar).with(token(cliente)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"123456\"}"))
                .andExpect(erro(403, "ACESSO_NEGADO", validar));
    }

    @Test
    @DisplayName("Código errado: 400 com o motivo no campo erro")
    void codigoErrado400() throws Exception {
        when(chegadaService.validar(agendamentoId, empresa.getId(), "000000"))
                .thenThrow(new CodigoChegadaException(CodigoChegadaException.Motivo.TOKEN_INVALIDO));

        mockMvc.perform(post(validar).with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"000000\"}"))
                .andExpect(erro(400, "TOKEN_INVALIDO", validar));
    }

    @Test
    @DisplayName("Validar: 200 com o agendamento de chegada confirmada")
    void validar200() throws Exception {
        LocalDateTime inicio = LocalDateTime.of(2030, 1, 10, 14, 0);
        AgendamentoResponse chegou = new AgendamentoResponse(agendamentoId, cliente.getId(), "Ana", empresa.getId(),
                "Estúdio", null, null, UUID.randomUUID(), "Corte", 60, inicio, inicio.plusHours(1),
                StatusAgendamento.CHEGADA_CONFIRMADA, new BigDecimal("80.00"), FormaPagamento.PIX, null);
        when(chegadaService.validar(agendamentoId, empresa.getId(), "123456")).thenReturn(chegou);

        mockMvc.perform(post(validar).with(token(empresa)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(agendamentoId.toString()))
                .andExpect(jsonPath("$.status").value("CHEGADA_CONFIRMADA"));
    }
}
