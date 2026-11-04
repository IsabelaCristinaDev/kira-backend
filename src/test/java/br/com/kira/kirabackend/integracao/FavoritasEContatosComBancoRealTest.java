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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class FavoritasEContatosComBancoRealTest {

    private static final String FAVORITAS = "/api/favoritas";
    private static final String CONTATOS = "/api/contatos-emergencia";

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

    private HttpRequest pedido(String metodo, String caminho, Object corpo, String token) throws Exception {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        return pedido.build();
    }

    private HttpResponse<String> chamar(String metodo, String caminho, Object corpo, String token) throws Exception {
        return http.send(pedido(metodo, caminho, corpo, token), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode esperar(int status, String metodo, String caminho, Object corpo, String token) throws Exception {
        HttpResponse<String> resposta = chamar(metodo, caminho, corpo, token);
        assertEquals(status, resposta.statusCode(), resposta.body());
        return resposta.body().isBlank() ? json.nullNode() : json.readTree(resposta.body());
    }

    private JsonNode entrar(String conta) throws Exception {
        return esperar(200, "POST", "/api/auth/login", Map.of("email", conta, "senha", "Senha1234"), null);
    }

    private JsonNode novaCliente(String nome, String cpf) throws Exception {
        String conta = nome + "@example.com";
        esperar(201, "POST", "/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", conta, "senha", "Senha1234", "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO",
                "endereco", Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                        "bairro", "Centro", "cidade", "Goiania", "estado", "GO")), null);
        return entrar(conta);
    }

    private JsonNode novoEstudio(String nome, String cpf) throws Exception {
        String conta = nome + "@example.com";
        esperar(201, "POST", "/api/auth/registro/empresa", Map.of(
                "nome", nome, "email", conta, "senha", "Senha1234", "telefone", "62999990001",
                "cpf", cpf, "dataNascimento", "1990-01-01", "especialidades", List.of("Cabelo"),
                "servicos", List.of(Map.of("nome", "Corte", "preco", "50", "duracao", "60")),
                "tipoEstabelecimento", "AUTONOMA"), null);
        return entrar(conta);
    }

    private Map<String, Object> contato(String nome) {
        return Map.of("nome", nome, "telefone", "62988887777", "parentesco", "Mãe");
    }

    @Test
    @DisplayName("Favoritas: salva, repete sem duplicar, lista só as da própria cliente e remove")
    void favoritas() throws Exception {
        String ana = novaCliente("ana", "52998224725").get("token").asText();
        String bia = novaCliente("bia", "11144477735").get("token").asText();
        String estudio = novoEstudio("estudioum", "39053344705").get("id").asText();

        esperar(204, "PUT", FAVORITAS + "/" + estudio, null, ana);
        esperar(204, "PUT", FAVORITAS + "/" + estudio, null, ana);

        JsonNode lista = esperar(200, "GET", FAVORITAS, null, ana);
        assertEquals(1, lista.size());
        assertEquals(estudio, lista.get(0).get("id").asText());
        assertEquals(0, esperar(200, "GET", FAVORITAS, null, bia).size());

        // Remover o que a outra salvou não muda nada para quem salvou
        esperar(204, "DELETE", FAVORITAS + "/" + estudio, null, bia);
        assertEquals(1, esperar(200, "GET", FAVORITAS, null, ana).size());

        esperar(204, "DELETE", FAVORITAS + "/" + estudio, null, ana);
        esperar(204, "DELETE", FAVORITAS + "/" + estudio, null, ana);
        assertEquals(0, esperar(200, "GET", FAVORITAS, null, ana).size());
    }

    @Test
    @DisplayName("Favoritas: salvar ao mesmo tempo dá certo e grava uma linha só")
    void favoritasSimultaneas() throws Exception {
        JsonNode carla = novaCliente("carla", "86288366757");
        String estudio = novoEstudio("estudiodois", "12345678909").get("id").asText();

        List<CompletableFuture<HttpResponse<String>>> respostas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            respostas.add(http.sendAsync(
                    pedido("PUT", FAVORITAS + "/" + estudio, null, carla.get("token").asText()),
                    HttpResponse.BodyHandlers.ofString()));
        }
        for (CompletableFuture<HttpResponse<String>> resposta : respostas) {
            assertEquals(204, resposta.get().statusCode(), resposta.get().body());
        }
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from favorita where cliente_id = ?::uuid and empresa_id = ?::uuid",
                Integer.class, carla.get("id").asText(), estudio));
    }

    @Test
    @DisplayName("Favoritas: estúdio inexistente ou desativado responde 404, e o desativado sai da lista")
    void favoritasEstudioDesativado() throws Exception {
        String dani = novaCliente("dani", "98765432100").get("token").asText();
        String estudio = novoEstudio("estudiotres", "71428793860").get("id").asText();

        esperar(404, "PUT", FAVORITAS + "/" + UUID.randomUUID(), null, dani);
        esperar(204, "PUT", FAVORITAS + "/" + estudio, null, dani);

        jdbc.update("update usuario set ativo = false where id = ?::uuid", estudio);

        assertEquals(0, esperar(200, "GET", FAVORITAS, null, dani).size());
        esperar(404, "PUT", FAVORITAS + "/" + estudio, null, dani);
    }

    @Test
    @DisplayName("Contatos: cadastra, lista e remove, sem acesso aos contatos de outra cliente")
    void contatos() throws Exception {
        String eva = novaCliente("eva", "15350946056").get("token").asText();
        String fer = novaCliente("fer", "74682489070").get("token").asText();

        JsonNode criado = esperar(201, "POST", CONTATOS, contato("Rosa"), eva);
        assertEquals("Rosa", criado.get("nome").asText());
        assertEquals("Mãe", criado.get("parentesco").asText());
        String id = criado.get("id").asText();

        assertEquals(1, esperar(200, "GET", CONTATOS, null, eva).size());
        assertEquals(0, esperar(200, "GET", CONTATOS, null, fer).size());

        esperar(404, "DELETE", CONTATOS + "/" + id, null, fer);
        assertEquals(1, esperar(200, "GET", CONTATOS, null, eva).size());

        esperar(204, "DELETE", CONTATOS + "/" + id, null, eva);
        assertEquals(0, esperar(200, "GET", CONTATOS, null, eva).size());
        esperar(404, "DELETE", CONTATOS + "/" + id, null, eva);
    }

    @Test
    @DisplayName("Contatos: recusa dados inválidos e o sexto contato")
    void contatosLimiteEValidacao() throws Exception {
        String gabi = novaCliente("gabi", "25829387000").get("token").asText();

        esperar(400, "POST", CONTATOS, Map.of("nome", " ", "telefone", "62988887777"), gabi);
        esperar(400, "POST", CONTATOS, Map.of("nome", "Rosa", "telefone", "1".repeat(21)), gabi);

        // Parentesco é opcional
        JsonNode semParentesco = esperar(201, "POST", CONTATOS, Map.of("nome", "Rosa", "telefone", "62988887777"), gabi);
        assertTrue(semParentesco.get("parentesco").isNull());

        for (int i = 0; i < 4; i++) {
            esperar(201, "POST", CONTATOS, contato("Pessoa " + i), gabi);
        }
        esperar(400, "POST", CONTATOS, contato("Sexta"), gabi);
        assertEquals(5, esperar(200, "GET", CONTATOS, null, gabi).size());
    }

    @Test
    @DisplayName("Sem login não entra, e estúdio não usa as rotas de cliente")
    void acesso() throws Exception {
        JsonNode estudio = novoEstudio("estudioquatro", "60247081010");
        String token = estudio.get("token").asText();

        assertTrue(chamar("GET", FAVORITAS, null, null).statusCode() >= 401);
        assertTrue(chamar("GET", CONTATOS, null, null).statusCode() >= 401);
        assertTrue(chamar("GET", FAVORITAS, null, null).statusCode() < 500);
        assertTrue(chamar("GET", CONTATOS, null, null).statusCode() < 500);

        esperar(400, "GET", FAVORITAS, null, token);
        esperar(400, "PUT", FAVORITAS + "/" + estudio.get("id").asText(), null, token);
        esperar(400, "GET", CONTATOS, null, token);
        esperar(400, "POST", CONTATOS, contato("Rosa"), token);
    }
}
