package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.config.AtualizacaoStatusAgendamento;
import br.com.kira.kirabackend.util.KiraTimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirmação presencial com o Postgres de verdade: a regra CHECK do status (que o ddl-auto não atualiza),
 * a trava do agendamento e as tentativas erradas gravadas mesmo com o erro devolvido ao app.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ChegadaComBancoRealTest {

    private static final String SENHA = "Senha1234";

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

    @Autowired
    private AtualizacaoStatusAgendamento atualizacaoStatus;

    @MockBean
    private JavaMailSender email;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private record Conta(String id, String token) {}

    private static Conta cliente;
    private static Conta outraCliente;
    private static Conta estudio;
    private static Conta outroEstudio;
    private static String servico;
    private static int proximaHora = 8;

    @BeforeEach
    void prepararContas() throws Exception {
        if (cliente != null) {
            return;
        }
        cliente = novaCliente("ana", "52998224725");
        outraCliente = novaCliente("bia", "11144477735");
        estudio = novoEstudio("salao", "11444777000161");
        outroEstudio = novoEstudio("outro", "11222333000181");
        servico = esperar(200, "GET", "/api/empresas/" + estudio.id() + "/servicos", null, estudio.token())
                .get(0).get("id").asText();
    }

    @Test
    @DisplayName("Banco criado antes do status novo: a regra CHECK é refeita na subida e a chegada é gravada")
    void regraAntigaDoBancoEhAtualizada() throws Exception {
        jdbc.execute("alter table agendamento drop constraint if exists agendamento_status_check");
        jdbc.execute("alter table agendamento add constraint agendamento_status_check check (status in"
                + " ('PENDENTE', 'CONFIRMADO', 'CANCELADO', 'CONCLUIDO', 'REAGENDADO'))");

        atualizacaoStatus.run(new DefaultApplicationArguments());

        String agendamento = agendamentoConfirmadoDaquiA30Minutos();
        String codigo = esperar(200, "POST", caminho(agendamento, "token-chegada"), null, cliente.token())
                .get("codigo").asText();
        JsonNode validado = esperar(200, "POST", caminho(agendamento, "chegada/validar"),
                Map.of("codigo", codigo), estudio.token());
        assertEquals("CHEGADA_CONFIRMADA", validado.get("status").asText());
    }

    @Test
    @DisplayName("Fluxo completo: gerar, ver de novo, errar, acertar e concluir o atendimento")
    void fluxoCompleto() throws Exception {
        String agendamento = agendamentoConfirmadoDaquiA30Minutos();

        JsonNode gerado = esperar(200, "POST", caminho(agendamento, "token-chegada"), null, cliente.token());
        String codigo = gerado.get("codigo").asText();
        assertTrue(codigo.matches("\\d{6}"), codigo);
        assertTrue(OffsetDateTime.parse(gerado.get("expiraEm").asText()).isAfter(OffsetDateTime.now()));

        // Ver de novo e gerar de novo devolvem o mesmo código enquanto ele vale
        assertEquals(codigo, esperar(200, "GET", caminho(agendamento, "token-chegada"), null, cliente.token())
                .get("codigo").asText());
        assertEquals(codigo, esperar(200, "POST", caminho(agendamento, "token-chegada"), null, cliente.token())
                .get("codigo").asText());

        // Só a cliente do agendamento vê; só o estúdio do agendamento valida
        esperar(403, "GET", caminho(agendamento, "token-chegada"), null, outraCliente.token());
        esperar(403, "GET", caminho(agendamento, "token-chegada"), null, estudio.token());
        esperar(403, "POST", caminho(agendamento, "chegada/validar"), Map.of("codigo", codigo), outroEstudio.token());
        esperar(403, "POST", caminho(agendamento, "chegada/validar"), Map.of("codigo", codigo), cliente.token());
        esperar(401, "GET", caminho(agendamento, "token-chegada"), null, null);

        // Código errado: o erro chega ao app e a tentativa fica gravada (a transação não é desfeita)
        JsonNode errado = esperar(400, "POST", caminho(agendamento, "chegada/validar"),
                Map.of("codigo", outroCodigo(codigo)), estudio.token());
        assertEquals("TOKEN_INVALIDO", errado.get("erro").asText());
        assertEquals(1, tentativasErradas(agendamento));
        esperar(400, "POST", caminho(agendamento, "chegada/validar"), Map.of("codigo", "12"), estudio.token());

        JsonNode validado = esperar(200, "POST", caminho(agendamento, "chegada/validar"),
                Map.of("codigo", codigo), estudio.token());
        assertEquals("CHEGADA_CONFIRMADA", validado.get("status").asText());

        // Segundo envio, código que não aparece mais, cliente sem cancelar nem remarcar
        assertEquals("TOKEN_JA_UTILIZADO", esperar(400, "POST", caminho(agendamento, "chegada/validar"),
                Map.of("codigo", codigo), estudio.token()).get("erro").asText());
        esperar(404, "GET", caminho(agendamento, "token-chegada"), null, cliente.token());
        esperar(400, "PATCH", "/api/agendamentos/" + agendamento + "/cancelar", null, cliente.token());

        // A chegada confirmada continua aparecendo na agenda e o estúdio conclui normalmente
        assertEquals("CHEGADA_CONFIRMADA", esperar(200, "GET", "/api/agendamentos/empresa/" + estudio.id(), null,
                estudio.token()).findValues("status").stream()
                .map(JsonNode::asText).filter("CHEGADA_CONFIRMADA"::equals).findFirst().orElse(""));
        assertEquals("CONCLUIDO", esperar(200, "PATCH", "/api/agendamentos/" + agendamento + "/concluir", null,
                estudio.token()).get("status").asText());
    }

    @Test
    @DisplayName("Depois de 5 tentativas erradas o código deixa de valer, e a cliente recebe outro")
    void bloqueioPorTentativas() throws Exception {
        String agendamento = agendamentoConfirmadoDaquiA30Minutos();
        String codigo = esperar(200, "POST", caminho(agendamento, "token-chegada"), null, cliente.token())
                .get("codigo").asText();

        List<String> erros = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            erros.add(esperar(400, "POST", caminho(agendamento, "chegada/validar"),
                    Map.of("codigo", outroCodigo(codigo)), estudio.token()).get("erro").asText());
        }
        assertEquals(List.of("TOKEN_INVALIDO", "TOKEN_INVALIDO", "TOKEN_INVALIDO", "TOKEN_INVALIDO", "TOKEN_EXPIRADO"),
                erros);
        assertEquals("TOKEN_EXPIRADO", esperar(400, "POST", caminho(agendamento, "chegada/validar"),
                Map.of("codigo", codigo), estudio.token()).get("erro").asText());
        esperar(404, "GET", caminho(agendamento, "token-chegada"), null, cliente.token());

        String novo = esperar(200, "POST", caminho(agendamento, "token-chegada"), null, cliente.token())
                .get("codigo").asText();
        assertEquals(0, tentativasErradas(agendamento));
        esperar(200, "POST", caminho(agendamento, "chegada/validar"), Map.of("codigo", novo), estudio.token());
    }

    @Test
    @DisplayName("Agendamento ainda pendente ou longe do horário: a cliente não gera o código")
    void foraDasCondicoes() throws Exception {
        String pendente = agendar(LocalDate.now(KiraTimeZone.DEFAULT).plusDays(3).atTime(proximaHora++, 0));
        esperar(400, "POST", caminho(pendente, "token-chegada"), null, cliente.token());

        esperar(200, "PATCH", "/api/agendamentos/" + pendente + "/confirmar", null, estudio.token());
        JsonNode cedo = esperar(400, "POST", caminho(pendente, "token-chegada"), null, cliente.token());
        assertEquals("O código de chegada fica disponível 2 horas antes do horário marcado",
                cedo.get("mensagem").asText());
    }

    // ---------- agendamento ----------

    /** Agenda para daqui a 3 dias (a API só aceita futuro), confirma e traz para daqui a 30 minutos no banco. */
    private String agendamentoConfirmadoDaquiA30Minutos() throws Exception {
        String id = agendar(LocalDate.now(KiraTimeZone.DEFAULT).plusDays(3).atTime(proximaHora++, 0));
        esperar(200, "PATCH", "/api/agendamentos/" + id + "/confirmar", null, estudio.token());
        LocalDateTime inicio = LocalDateTime.now(KiraTimeZone.DEFAULT).plusMinutes(30).withNano(0);
        jdbc.update("update agendamento set data_hora_inicio = ?, data_hora_fim = ? where id = ?::uuid",
                inicio, inicio.plusMinutes(60), id);
        return id;
    }

    private String agendar(LocalDateTime inicio) throws Exception {
        return esperar(201, "POST", "/api/agendamentos/cliente/" + cliente.id(), Map.of(
                "empresaId", estudio.id(), "servicoId", servico,
                "dataHoraInicio", inicio.toString(), "formaPagamento", "DINHEIRO"), cliente.token()).get("id").asText();
    }

    private int tentativasErradas(String agendamento) {
        return jdbc.queryForObject("select tentativas_erradas from codigo_chegada where agendamento_id = ?::uuid",
                Integer.class, agendamento);
    }

    private static String outroCodigo(String codigo) {
        String outro = codigo.equals("000000") ? "111111" : "000000";
        assertNotEquals(codigo, outro);
        return outro;
    }

    private static String caminho(String agendamento, String resto) {
        return "/api/agendamentos/" + agendamento + "/" + resto;
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
