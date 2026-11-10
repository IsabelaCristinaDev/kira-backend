package br.com.kira.kirabackend.integracao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class TrocaDeSenhaComBancoRealTest {

    private static final String SENHA = "/api/auth/senha";
    private static final String SENHA_INICIAL = "Senha1234";
    private static final String SENHA_NOVA = "OutraSenha99";

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

    @MockBean
    private JavaMailSender email;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> chamar(String metodo, String caminho, Object corpo, String token) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode esperar(int status, String metodo, String caminho, Object corpo, String token) throws Exception {
        HttpResponse<String> resposta = chamar(metodo, caminho, corpo, token);
        assertEquals(status, resposta.statusCode(), resposta.body());
        return resposta.body().isBlank() ? json.nullNode() : json.readTree(resposta.body());
    }

    private HttpResponse<String> entrar(String conta, String senha) throws Exception {
        return chamar("POST", "/api/auth/login", Map.of("email", conta, "senha", senha), null);
    }

    private JsonNode novaCliente(String nome, String cpf) throws Exception {
        String conta = nome + "@example.com";
        esperar(201, "POST", "/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", conta, "senha", SENHA_INICIAL, "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO",
                "endereco", Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                        "bairro", "Centro", "cidade", "Goiania", "estado", "GO")), null);
        return json.readTree(entrar(conta, SENHA_INICIAL).body());
    }

    private JsonNode novoEstudio(String nome, String cpf) throws Exception {
        String conta = nome + "@example.com";
        esperar(201, "POST", "/api/auth/registro/empresa", Map.of(
                "nome", nome, "email", conta, "senha", SENHA_INICIAL, "telefone", "62999990001",
                "cpf", cpf, "dataNascimento", "1990-01-01", "especialidades", List.of("Cabelo"),
                "servicos", List.of(Map.of("nome", "Corte", "preco", "50", "duracao", "60")),
                "tipoEstabelecimento", "AUTONOMA"), null);
        return json.readTree(entrar(conta, SENHA_INICIAL).body());
    }

    private static Map<String, String> troca(String atual, String nova) {
        return Map.of("senhaAtual", atual, "novaSenha", nova);
    }

    @Test
    @DisplayName("Cliente troca a senha: a sessão antiga cai, a nova vale e só a senha nova entra")
    void clienteTrocaSenha() throws Exception {
        JsonNode sessao = novaCliente("ana", "52998224725");
        String token = sessao.get("token").asText();
        String refreshAntigo = sessao.get("refreshToken").asText();

        JsonNode nova = esperar(200, "PATCH", SENHA, troca(SENHA_INICIAL, SENHA_NOVA), token);
        assertEquals("CLIENTE", nova.get("tipo").asText());
        assertEquals(sessao.get("id").asText(), nova.get("id").asText());
        assertNotEquals(refreshAntigo, nova.get("refreshToken").asText());

        // A sessão de antes não renova mais; a devolvida na troca renova
        esperar(400, "POST", "/api/auth/refresh", Map.of("refreshToken", refreshAntigo), null);
        esperar(200, "POST", "/api/auth/refresh", Map.of("refreshToken", nova.get("refreshToken").asText()), null);

        assertEquals(401, entrar("ana@example.com", SENHA_INICIAL).statusCode());
        assertEquals(200, entrar("ana@example.com", SENHA_NOVA).statusCode());
    }

    @Test
    @DisplayName("Estúdio também troca a senha")
    void estudioTrocaSenha() throws Exception {
        String token = novoEstudio("estudioum", "39053344705").get("token").asText();

        JsonNode nova = esperar(200, "PATCH", SENHA, troca(SENHA_INICIAL, SENHA_NOVA), token);

        assertEquals("EMPRESA", nova.get("tipo").asText());
        assertEquals(200, entrar("estudioum@example.com", SENHA_NOVA).statusCode());
    }

    @Test
    @DisplayName("Senha atual errada, nova curta, nova igual ou campo vazio dão 400 e a senha não muda")
    void recusas() throws Exception {
        JsonNode sessao = novaCliente("bia", "11144477735");
        String token = sessao.get("token").asText();

        JsonNode errada = esperar(400, "PATCH", SENHA, troca("Errada999", SENHA_NOVA), token);
        assertEquals("Senha atual incorreta", errada.get("mensagem").asText());

        JsonNode curta = esperar(400, "PATCH", SENHA, troca(SENHA_INICIAL, "curta"), token);
        assertTrue(curta.get("erros").has("novaSenha"));

        esperar(400, "PATCH", SENHA, troca(SENHA_INICIAL, SENHA_INICIAL), token);
        esperar(400, "PATCH", SENHA, Map.of("novaSenha", SENHA_NOVA), token);

        // Nada mudou: a senha de antes ainda entra
        assertEquals(200, entrar("bia@example.com", SENHA_INICIAL).statusCode());
    }

    @Test
    @DisplayName("Sem login não troca a senha")
    void semLogin() throws Exception {
        int status = chamar("PATCH", SENHA, troca(SENHA_INICIAL, SENHA_NOVA), null).statusCode();

        assertTrue(status == 401 || status == 403, "status " + status);
    }
}
