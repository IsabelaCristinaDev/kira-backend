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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Serviço desativado pelo estúdio, de ponta a ponta pela API e com o Postgres de verdade: a cliente com a
 * tela desatualizada ainda manda o id dele, e o backend recusa sem gravar nada.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ServicoDesativadoComBancoRealTest {

    private static final String SENHA = "Senha1234";
    private static final String SERVICO_INDISPONIVEL = "Este serviço não está mais disponível";

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

    private static Conta cliente;
    private static Conta estudio;
    /** Cada agendamento pega um horário diferente no dia de daqui a 3 dias. */
    private static int proximaHora = 8;

    @BeforeEach
    void prepararContas() throws Exception {
        if (cliente != null) {
            return;
        }
        cliente = novaCliente("ana", "52998224725");
        estudio = novoEstudio("salao", "11444777000161");
    }

    @Test
    @DisplayName("Serviço desativado: o agendamento é recusado, nada é gravado e outro serviço ativo agenda normalmente")
    void servicoDesativadoEhRecusado() throws Exception {
        String desativado = novoServico("Escova");
        String ativo = novoServico("Hidratação");
        esperar(204, "DELETE", "/api/empresas/" + estudio.id() + "/servicos/" + desativado, null, estudio.token());

        // Some da lista que a cliente vê, mas a tela dela pode ter sido carregada antes
        assertFalse(idsDosServicos().contains(desativado));
        assertTrue(idsDosServicos().contains(ativo));
        int antes = agendamentosDaCliente();

        JsonNode erro = esperar(400, "POST", "/api/agendamentos/cliente/" + cliente.id(),
                corpoDoAgendamento(desativado), cliente.token());

        assertEquals("REGRA_DE_NEGOCIO", erro.get("erro").asText());
        assertEquals(SERVICO_INDISPONIVEL, erro.get("mensagem").asText());
        assertEquals(antes, agendamentosDaCliente());
        assertEquals(0, agendamentosDoServico(desativado));

        JsonNode criado = esperar(201, "POST", "/api/agendamentos/cliente/" + cliente.id(),
                corpoDoAgendamento(ativo), cliente.token());
        assertEquals(1, agendamentosDoServico(ativo));
        assertEquals(antes + 1, agendamentosDaCliente());
        assertEquals("PENDENTE", criado.get("status").asText());
    }

    @Test
    @DisplayName("Serviço reativado: volta a receber agendamento")
    void servicoReativadoVoltaAAgendar() throws Exception {
        String servico = novoServico("Corte curto");
        esperar(204, "DELETE", "/api/empresas/" + estudio.id() + "/servicos/" + servico, null, estudio.token());
        esperar(400, "POST", "/api/agendamentos/cliente/" + cliente.id(), corpoDoAgendamento(servico), cliente.token());

        // A API não tem rota para reativar um serviço: a reativação é feita direto no banco
        jdbc.update("update servico set ativo = true where id = ?::uuid", servico);

        esperar(201, "POST", "/api/agendamentos/cliente/" + cliente.id(), corpoDoAgendamento(servico), cliente.token());
        assertEquals(1, agendamentosDoServico(servico));
        assertTrue(idsDosServicos().contains(servico));
    }

    // ---------- serviços e agendamentos ----------

    private String novoServico(String nome) throws Exception {
        return esperar(201, "POST", "/api/empresas/" + estudio.id() + "/servicos",
                Map.of("nome", nome, "duracaoMinutos", 60, "preco", 50), estudio.token()).get("id").asText();
    }

    private List<String> idsDosServicos() throws Exception {
        JsonNode lista = esperar(200, "GET", "/api/empresas/" + estudio.id() + "/servicos", null, cliente.token());
        return lista.findValues("id").stream().map(JsonNode::asText).toList();
    }

    private Map<String, Object> corpoDoAgendamento(String servico) {
        return Map.of("empresaId", estudio.id(), "servicoId", servico,
                "dataHoraInicio", LocalDate.now(KiraTimeZone.DEFAULT).plusDays(3).atTime(proximaHora++, 0).toString(),
                "formaPagamento", "DINHEIRO");
    }

    private int agendamentosDaCliente() {
        return jdbc.queryForObject("select count(*) from agendamento where cliente_id = ?::uuid", Integer.class,
                cliente.id());
    }

    private int agendamentosDoServico(String servico) {
        return jdbc.queryForObject("select count(*) from agendamento where servico_id = ?::uuid", Integer.class,
                servico);
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
