package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.exception.GeracaoProvadorException;
import br.com.kira.kirabackend.service.GeradorDeImagem;
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

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A contagem é uma operação do banco: só dá para conferir com o Postgres e pedidos ao mesmo tempo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class LimiteProvadorComBancoRealTest {

    private static final int LIMITE = 5;
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final String LIMITE_ATINGIDO = "PROVADOR_LIMITE_DIARIO";

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

    @MockBean
    private GeradorDeImagem gerador;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void geradorFunciona() {
        reset(gerador);
        when(gerador.gerar(any(), any())).thenReturn(JPEG);
    }

    private HttpResponse<String> postar(String caminho, Object corpo, String token) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Conta nova, com o termo aceito. */
    private JsonNode entrar(String nome, String cpf) throws Exception {
        String conta = nome + "@example.com";
        HttpResponse<String> registro = postar("/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", conta, "senha", "Senha1234", "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO",
                "endereco", Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                        "bairro", "Centro", "cidade", "Goiania", "estado", "GO")), null);
        assertEquals(201, registro.statusCode(), registro.body());
        HttpResponse<String> login = postar("/api/auth/login", Map.of("email", conta, "senha", "Senha1234"), null);
        assertEquals(200, login.statusCode(), login.body());
        JsonNode sessao = json.readTree(login.body());
        HttpResponse<String> termo = postar(
                "/api/provador/consentimento", Map.of("versao", "v1"), sessao.get("token").asText());
        assertEquals(200, termo.statusCode(), termo.body());
        return sessao;
    }

    private HttpRequest pedidoDeGeracao(String token) throws Exception {
        String divisa = UUID.randomUUID().toString();
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        for (String parte : List.of("cliente", "referencia")) {
            corpo.write(("--" + divisa + "\r\nContent-Disposition: form-data; name=\"" + parte
                    + "\"; filename=\"" + parte + ".jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            corpo.write(JPEG);
            corpo.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        corpo.write(("--" + divisa + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/provador/geracoes"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + divisa)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                .build();
    }

    private HttpResponse<String> gerar(String token) throws Exception {
        return http.send(pedidoDeGeracao(token), HttpResponse.BodyHandlers.ofString());
    }

    private void gerarAteOLimite(String token) throws Exception {
        for (int i = 0; i < LIMITE; i++) {
            HttpResponse<String> resposta = gerar(token);
            assertEquals(200, resposta.statusCode(), resposta.body());
        }
    }

    private void confereRecusa(HttpResponse<String> resposta) throws Exception {
        assertEquals(429, resposta.statusCode(), resposta.body());
        assertEquals(LIMITE_ATINGIDO, json.readTree(resposta.body()).get("erro").asText());
    }

    @Test
    @DisplayName("A sexta prévia do dia é recusada sem chamar o gerador, e a outra conta continua livre")
    void sextaRecusada() throws Exception {
        String token = entrar("seis", "52998224725").get("token").asText();
        gerarAteOLimite(token);

        confereRecusa(gerar(token));

        verify(gerador, times(LIMITE)).gerar(any(), any());
        assertEquals(200, gerar(entrar("vizinha", "11144477735").get("token").asText()).statusCode());
    }

    @Test
    @DisplayName("No dia seguinte a contagem recomeça, na mesma linha")
    void diaSeguinte() throws Exception {
        JsonNode sessao = entrar("ontem", "39053344705");
        String token = sessao.get("token").asText();
        String id = sessao.get("id").asText();
        gerarAteOLimite(token);
        confereRecusa(gerar(token));

        LocalDate hoje = LocalDate.now(KiraTimeZone.DEFAULT);
        jdbc.update("update uso_provador set dia = ? where usuario_id = ?::uuid", hoje.minusDays(1), id);

        assertEquals(200, gerar(token).statusCode());
        assertEquals(1, jdbc.queryForObject(
                "select quantidade from uso_provador where usuario_id = ?::uuid", Integer.class, id));
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from uso_provador where usuario_id = ?::uuid", Integer.class, id));
    }

    @Test
    @DisplayName("Geração que falha não gasta as prévias do dia")
    void falhaNaoConta() throws Exception {
        String token = entrar("falha", "86288366757").get("token").asText();
        when(gerador.gerar(any(), any())).thenThrow(new GeracaoProvadorException("fora do ar"));
        for (int i = 0; i < LIMITE + 2; i++) {
            assertEquals(502, gerar(token).statusCode());
        }

        reset(gerador);
        when(gerador.gerar(any(), any())).thenReturn(JPEG);

        gerarAteOLimite(token);
        confereRecusa(gerar(token));
    }

    @Test
    @DisplayName("Pedidos ao mesmo tempo não passam do limite")
    void pedidosAoMesmoTempo() throws Exception {
        String token = entrar("juntos", "12345678909").get("token").asText();

        List<CompletableFuture<HttpResponse<String>>> respostas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            respostas.add(http.sendAsync(pedidoDeGeracao(token), HttpResponse.BodyHandlers.ofString()));
        }

        int aceitos = 0;
        for (CompletableFuture<HttpResponse<String>> resposta : respostas) {
            int status = resposta.get().statusCode();
            assertTrue(status == 200 || status == 429, resposta.get().body());
            aceitos += status == 200 ? 1 : 0;
        }
        assertEquals(LIMITE, aceitos);
        verify(gerador, times(LIMITE)).gerar(any(), any());
    }
}
