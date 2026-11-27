package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.util.KiraTimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prazo de 24 horas da cliente para cancelar ou remarcar, de ponta a ponta pela API e com o Postgres de
 * verdade: o pedido recusado não pode deixar nada mudado no banco, e o estúdio continua sem prazo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class PrazoDeCancelamentoComBancoRealTest {

    private static final String SENHA = "Senha1234";
    private static final String CANCELAMENTO_EM_CIMA_DA_HORA =
            "Cancelamento pelo cliente deve ser feito com no mínimo 24 horas de antecedência";
    private static final String REMARCACAO_EM_CIMA_DA_HORA =
            "Remarcação pelo cliente deve ser feita com no mínimo 24 horas de antecedência";

    @Container
    private static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BANCO::getJdbcUrl);
        registro.add("spring.datasource.username", BANCO::getUsername);
        registro.add("spring.datasource.password", BANCO::getPassword);
        registro.add("jwt.secret", () -> "segredo-usado-so-neste-teste-".repeat(4));
        registro.add("jwt.refresh-expiration", () -> "604800000");
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private JavaMailSender email;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private record Conta(String id, String token) {}

    /** Como fica o agendamento no banco: o que o pedido recusado não pode mudar. */
    private record Gravado(String status, LocalDateTime inicio, LocalDateTime fim) {}

    private static Conta cliente;
    private static Conta estudio;
    private static String servico;
    /** Cada agendamento pega um horário diferente no dia de daqui a 3 dias. */
    private static int proximaHora = 8;
    /** Os remarcados vão para horários diferentes no dia de daqui a 5 dias. */
    private static int proximaHoraRemarcada = 8;

    @BeforeEach
    void prepararContas() throws Exception {
        if (cliente != null) {
            return;
        }
        cliente = novaCliente("ana", "52998224725");
        estudio = novoEstudio("salao", "11444777000161");
        servico = esperar(200, "GET", "/api/empresas/" + estudio.id() + "/servicos", null, estudio.token())
                .get(0).get("id").asText();
    }

    // ---------- cliente com mais de 24 horas ----------

    @Test
    @DisplayName("Cliente com mais de 24 horas: cancela e o agendamento fica cancelado no banco")
    void clienteCancelaComAntecedencia() throws Exception {
        String agendamento = agendar();

        JsonNode cancelado = esperar(200, "PATCH", "/api/agendamentos/" + agendamento + "/cancelar", null,
                cliente.token());

        assertEquals("CANCELADO", cancelado.get("status").asText());
        assertEquals("CANCELADO", gravado(agendamento).status());
    }

    @Test
    @DisplayName("Cliente com pouco mais de 24 horas (borda): ainda cancela")
    void clienteCancelaLogoAcimaDoPrazo() throws Exception {
        String agendamento = agendar();
        moverPara(agendamento, agora().plusHours(24).plusMinutes(10));

        esperar(200, "PATCH", "/api/agendamentos/" + agendamento + "/cancelar", null, cliente.token());

        assertEquals("CANCELADO", gravado(agendamento).status());
    }

    @Test
    @DisplayName("Cliente com mais de 24 horas: remarca e o banco guarda o novo horário")
    void clienteRemarcaComAntecedencia() throws Exception {
        String agendamento = agendar();
        LocalDateTime novoInicio = proximoHorarioRemarcado();

        JsonNode remarcado = esperar(200, "PATCH", "/api/agendamentos/" + agendamento + "/reagendar/cliente",
                Map.of("novaDataHoraInicio", novoInicio.toString()), cliente.token());

        assertEquals("REAGENDADO", remarcado.get("status").asText());
        assertEquals(new Gravado("REAGENDADO", novoInicio, novoInicio.plusMinutes(60)), gravado(agendamento));
    }

    // ---------- cliente com menos de 24 horas ----------

    @Test
    @DisplayName("Cliente com menos de 24 horas: cancelar é recusado com a mensagem do prazo e nada muda no banco")
    void clienteNaoCancelaEmCimaDaHora() throws Exception {
        String agendamento = agendar();
        moverPara(agendamento, agora().plusHours(23));
        Gravado antes = gravado(agendamento);

        JsonNode erro = esperar(400, "PATCH", "/api/agendamentos/" + agendamento + "/cancelar", null,
                cliente.token());

        assertEquals("REGRA_DE_NEGOCIO", erro.get("erro").asText());
        assertEquals(CANCELAMENTO_EM_CIMA_DA_HORA, erro.get("mensagem").asText());
        assertEquals(antes, gravado(agendamento));
        assertEquals("PENDENTE", antes.status());
    }

    @Test
    @DisplayName("Cliente com menos de 24 horas: remarcar é recusado com a mensagem do prazo e nada muda no banco")
    void clienteNaoRemarcaEmCimaDaHora() throws Exception {
        String agendamento = agendar();
        esperar(200, "PATCH", "/api/agendamentos/" + agendamento + "/confirmar", null, estudio.token());
        moverPara(agendamento, agora().plusHours(3));
        Gravado antes = gravado(agendamento);

        JsonNode erro = esperar(400, "PATCH", "/api/agendamentos/" + agendamento + "/reagendar/cliente",
                Map.of("novaDataHoraInicio", proximoHorarioRemarcado().toString()), cliente.token());

        assertEquals("REGRA_DE_NEGOCIO", erro.get("erro").asText());
        assertEquals(REMARCACAO_EM_CIMA_DA_HORA, erro.get("mensagem").asText());
        assertEquals(antes, gravado(agendamento));
        assertEquals("CONFIRMADO", antes.status());
    }

    // ---------- estúdio, sem prazo ----------

    @Test
    @DisplayName("Estúdio com menos de 24 horas: cancela normalmente")
    void estudioCancelaEmCimaDaHora() throws Exception {
        String agendamento = agendar();
        moverPara(agendamento, agora().plusHours(2));

        JsonNode cancelado = esperar(200, "PATCH", "/api/agendamentos/" + agendamento + "/cancelar/empresa", null,
                estudio.token());

        assertEquals("CANCELADO", cancelado.get("status").asText());
        assertEquals("CANCELADO", gravado(agendamento).status());
    }

    @Test
    @DisplayName("Estúdio com menos de 24 horas: remarca normalmente e o banco guarda o novo horário")
    void estudioRemarcaEmCimaDaHora() throws Exception {
        String agendamento = agendar();
        moverPara(agendamento, agora().plusHours(2));
        LocalDateTime novoInicio = proximoHorarioRemarcado();

        JsonNode remarcado = esperar(200, "PATCH", "/api/agendamentos/" + agendamento + "/reagendar/empresa",
                Map.of("novaDataHoraInicio", novoInicio.toString()), estudio.token());

        assertEquals("REAGENDADO", remarcado.get("status").asText());
        assertEquals(new Gravado("REAGENDADO", novoInicio, novoInicio.plusMinutes(60)), gravado(agendamento));
    }

    // ---------- agendamento ----------

    /** A API só aceita horários futuros dentro do expediente: agenda para daqui a 3 dias. */
    private String agendar() throws Exception {
        LocalDateTime inicio = LocalDate.now(KiraTimeZone.DEFAULT).plusDays(3).atTime(proximaHora++, 0);
        return esperar(201, "POST", "/api/agendamentos/cliente/" + cliente.id(), Map.of(
                "empresaId", estudio.id(), "servicoId", servico,
                "dataHoraInicio", inicio.toString(), "formaPagamento", "DINHEIRO"), cliente.token()).get("id").asText();
    }

    private static LocalDateTime proximoHorarioRemarcado() {
        return LocalDate.now(KiraTimeZone.DEFAULT).plusDays(5).atTime(proximaHoraRemarcada++, 0);
    }

    /** Traz o agendamento para perto da hora direto no banco, já que a API não deixa agendar em cima da hora. */
    private void moverPara(String agendamento, LocalDateTime inicio) {
        jdbc.update("update agendamento set data_hora_inicio = ?, data_hora_fim = ? where id = ?::uuid",
                inicio, inicio.plusMinutes(60), agendamento);
    }

    private Gravado gravado(String agendamento) {
        return jdbc.queryForObject(
                "select status, data_hora_inicio, data_hora_fim from agendamento where id = ?::uuid",
                (linha, n) -> new Gravado(linha.getString(1),
                        linha.getTimestamp(2).toLocalDateTime(), linha.getTimestamp(3).toLocalDateTime()),
                agendamento);
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT).truncatedTo(ChronoUnit.SECONDS);
    }

    // ---------- contas ----------

    private Conta novaCliente(String nome, String cpf) throws Exception {
        esperar(201, "POST", "/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO", "endereco", endereco()), null);
        return entrar(nome);
    }

    private Conta novoEstudio(String nome, String cnpj) throws Exception {
        esperar(201, "POST", "/api/auth/registro/empresa", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990001",
                "cnpj", cnpj, "descricao", "Cortes", "endereco", endereco(),
                "tipoEstabelecimento", "ESTABELECIMENTO",
                "servicos", List.of(Map.of("nome", "Corte", "preco", "80", "duracao", "60"))), null);
        Conta conta = entrar(nome);
        List<Map<String, String>> semana = List.of("SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO", "DOMINGO")
                .stream().map(d -> Map.of("diaSemana", d, "horaInicio", "07:00", "horaFim", "22:00")).toList();
        esperar(200, "PUT", "/api/empresas/" + conta.id() + "/disponibilidade", Map.of("dias", semana), conta.token());
        return conta;
    }

    private Conta entrar(String nome) throws Exception {
        JsonNode login = esperar(200, "POST", "/api/auth/login",
                Map.of("email", nome + "@example.com", "senha", SENHA), null);
        return new Conta(login.get("id").asText(), login.get("token").asText());
    }

    private Map<String, Object> endereco() {
        return Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                "bairro", "Centro", "cidade", "Goiania", "estado", "GO");
    }

    // ---------- HTTP ----------

    private JsonNode esperar(int status, String metodo, String caminho, Object corpo, String token) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resposta = http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, resposta.statusCode(), metodo + " " + caminho + " -> " + resposta.body());
        return resposta.body().isBlank() ? json.nullNode() : json.readTree(resposta.body());
    }
}
