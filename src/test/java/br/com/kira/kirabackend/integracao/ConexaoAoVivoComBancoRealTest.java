package br.com.kira.kirabackend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Abre uma conexão de verdade no /ws e confere que o servidor a encerra sozinho.
 * Token curto e verificação frequente, para o teste não esperar 1 hora.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ConexaoAoVivoComBancoRealTest {

    @Container
    private static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BANCO::getJdbcUrl);
        registro.add("spring.datasource.username", BANCO::getUsername);
        registro.add("spring.datasource.password", BANCO::getPassword);
        registro.add("jwt.secret", () -> "segredo-usado-so-neste-teste-".repeat(4));
        registro.add("jwt.refresh-expiration", () -> "604800000");
        registro.add("jwt.expiration", () -> "6000");
        registro.add("kira.websocket.verificacao-ms", () -> "200");
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private JavaMailSender email;

    private final ObjectMapper json = new ObjectMapper();
    private StompSession aberta;

    // Se um teste falhar, a conexão não fica aberta consultando o banco depois de ele ser desligado
    @AfterEach
    void fechar() {
        if (aberta != null && aberta.isConnected()) {
            aberta.disconnect();
        }
    }

    private JsonNode postar(String caminho, Map<String, Object> corpo, int esperado) throws Exception {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)))
                .build();
        HttpResponse<String> resposta = HttpClient.newHttpClient().send(pedido, HttpResponse.BodyHandlers.ofString());
        assertEquals(esperado, resposta.statusCode(), resposta.body());
        return resposta.body().isBlank() ? json.nullNode() : json.readTree(resposta.body());
    }

    private JsonNode entrarComNovaConta(String nome, String cpf) throws Exception {
        String conta = nome + "@example.com";
        postar("/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", conta, "senha", "Senha1234", "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO",
                "endereco", Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                        "bairro", "Centro", "cidade", "Goiania", "estado", "GO")), 201);
        return postar("/api/auth/login", Map.of("email", conta, "senha", "Senha1234"), 200);
    }

    private record Conexao(StompSession sessao, CompletableFuture<String> recusa) {
    }

    private Conexao conectar(String token) throws Exception {
        CompletableFuture<String> recusa = new CompletableFuture<>();
        StompHeaders cabecalhos = new StompHeaders();
        cabecalhos.add("Authorization", "Bearer " + token);
        StompSession sessao = new WebSocketStompClient(new StandardWebSocketClient())
                .connectAsync(URI.create("ws://localhost:" + porta + "/ws"), new WebSocketHttpHeaders(), cabecalhos,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleFrame(StompHeaders frame, Object corpo) {
                                recusa.complete(frame.getFirst("message"));
                            }
                        })
                .get(5, TimeUnit.SECONDS);
        aberta = sessao;
        return new Conexao(sessao, recusa);
    }

    private void esperarFechar(StompSession sessao) {
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> !sessao.isConnected());
    }

    @Test
    @DisplayName("Token vencido encerra a conexão aberta, com a mensagem que faz o app renovar")
    void tokenVencido() throws Exception {
        JsonNode login = entrarComNovaConta("ana", "52998224725");

        Conexao conexao = conectar(login.get("token").asText());

        assertEquals("Token expirado", conexao.recusa().get(15, TimeUnit.SECONDS));
        esperarFechar(conexao.sessao());
    }

    @Test
    @DisplayName("Conta desativada encerra a conexão aberta, mesmo com o token ainda válido")
    void contaDesativada() throws Exception {
        JsonNode login = entrarComNovaConta("bia", "11144477735");
        Conexao conexao = conectar(login.get("token").asText());

        jdbc.update("update usuario set ativo = false where id = ?", UUID.fromString(login.get("id").asText()));

        assertEquals("Conta desativada", conexao.recusa().get(5, TimeUnit.SECONDS));
        esperarFechar(conexao.sessao());
    }
}
