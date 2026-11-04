package br.com.kira.kirabackend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** O conflito de registros simultâneos só aparece com o banco de verdade e pedidos ao mesmo tempo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class RegistroDispositivoComBancoRealTest {

    private static final String ENDERECO = "https://fcm.googleapis.com/fcm/send/";
    private static final String DISPOSITIVOS = "/api/notificacoes/dispositivos/web";
    // Um ponto válido da curva P-256 e 16 bytes, que é o formato exigido
    private static final String P256DH =
            "BGsX0fLhLEJH-Lzm5WOkQPJ3A32BLeszoPShOUXYmMKWT-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU";
    private static final String AUTH = "AAAAAAAAAAAAAAAAAAAAAA";

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

    private HttpRequest pedido(String caminho, Object corpo, String token) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return pedido.build();
    }

    private JsonNode entrarComNovaConta(String nome, String cpf) throws Exception {
        String conta = nome + "@example.com";
        HttpResponse<String> registro = http.send(pedido("/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", conta, "senha", "Senha1234", "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO",
                "endereco", Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                        "bairro", "Centro", "cidade", "Goiania", "estado", "GO")), null),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, registro.statusCode(), registro.body());
        HttpResponse<String> login = http.send(
                pedido("/api/auth/login", Map.of("email", conta, "senha", "Senha1234"), null),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, login.statusCode(), login.body());
        return json.readTree(login.body());
    }

    private HttpRequest registrar(String endereco, String token) throws Exception {
        return pedido(DISPOSITIVOS, Map.of("endpoint", endereco, "p256dh", P256DH, "auth", AUTH), token);
    }

    private void registrarUm(String endereco, String token) throws Exception {
        HttpResponse<String> resposta = http.send(registrar(endereco, token), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, resposta.statusCode(), resposta.body());
    }

    private int contar(String endereco) {
        return jdbc.queryForObject(
                "select count(*) from dispositivo_notificacao where endpoint = ?", Integer.class, endereco);
    }

    @Test
    @DisplayName("Registros simultâneos do mesmo navegador dão certo e gravam uma linha só")
    void registrosSimultaneos() throws Exception {
        String token = entrarComNovaConta("simultaneo", "52998224725").get("token").asText();

        for (int rodada = 0; rodada < 15; rodada++) {
            String endereco = ENDERECO + UUID.randomUUID();
            List<CompletableFuture<HttpResponse<String>>> respostas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                respostas.add(http.sendAsync(registrar(endereco, token), HttpResponse.BodyHandlers.ofString()));
            }
            for (CompletableFuture<HttpResponse<String>> resposta : respostas) {
                assertEquals(204, resposta.get().statusCode(), resposta.get().body());
            }
            assertEquals(1, contar(endereco));
        }
    }

    @Test
    @DisplayName("Registrar de novo mantém a linha, e outra conta passa a ser a dona")
    void mudaDeConta() throws Exception {
        JsonNode primeira = entrarComNovaConta("primeira", "11144477735");
        JsonNode segunda = entrarComNovaConta("segunda", "39053344705");
        String endereco = ENDERECO + UUID.randomUUID();

        registrarUm(endereco, primeira.get("token").asText());
        UUID id = jdbc.queryForObject(
                "select id from dispositivo_notificacao where endpoint = ?", UUID.class, endereco);
        registrarUm(endereco, primeira.get("token").asText());
        registrarUm(endereco, segunda.get("token").asText());

        assertEquals(1, contar(endereco));
        assertEquals(id, jdbc.queryForObject(
                "select id from dispositivo_notificacao where endpoint = ?", UUID.class, endereco));
        assertEquals(segunda.get("id").asText(), jdbc.queryForObject(
                "select usuario_id::text from dispositivo_notificacao where endpoint = ?", String.class, endereco));
    }

    @Test
    @DisplayName("Passando do limite, sai o navegador mais antigo")
    void limitePorPessoa() throws Exception {
        JsonNode conta = entrarComNovaConta("limite", "86288366757");
        String token = conta.get("token").asText();
        String maisAntigo = ENDERECO + UUID.randomUUID();
        registrarUm(maisAntigo, token);

        for (int i = 0; i < 10; i++) {
            registrarUm(ENDERECO + UUID.randomUUID(), token);
        }

        assertEquals(0, contar(maisAntigo));
        assertEquals(10, jdbc.queryForObject(
                "select count(*) from dispositivo_notificacao where usuario_id = ?::uuid",
                Integer.class, conta.get("id").asText()));
    }

    @Test
    @DisplayName("No limite, navegadores diferentes ao mesmo tempo dão certo, e receber um de outra conta também conta")
    void limiteAoMesmoTempoEMudandoDeConta() throws Exception {
        JsonNode conta = entrarComNovaConta("cheia", "12345678909");
        String token = conta.get("token").asText();
        for (int i = 0; i < 10; i++) {
            registrarUm(ENDERECO + UUID.randomUUID(), token);
        }

        for (int rodada = 0; rodada < 5; rodada++) {
            List<CompletableFuture<HttpResponse<String>>> respostas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                respostas.add(http.sendAsync(
                        registrar(ENDERECO + UUID.randomUUID(), token), HttpResponse.BodyHandlers.ofString()));
            }
            for (CompletableFuture<HttpResponse<String>> resposta : respostas) {
                assertEquals(204, resposta.get().statusCode(), resposta.get().body());
            }
        }

        String deOutraConta = ENDERECO + UUID.randomUUID();
        registrarUm(deOutraConta, entrarComNovaConta("outra", "98765432100").get("token").asText());
        registrarUm(deOutraConta, token);

        assertEquals(10, jdbc.queryForObject(
                "select count(*) from dispositivo_notificacao where usuario_id = ?::uuid",
                Integer.class, conta.get("id").asText()));
    }
}
