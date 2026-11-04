package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.config.PreenchimentoCategoriaServicos;
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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class BuscaEstudioComBancoRealTest {

    private static final String BUSCA = "/api/studios/pesquisa";

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
    private PreenchimentoCategoriaServicos preenchimento;

    @MockBean
    private JavaMailSender email;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    // Os mesmos dados servem para todos os testes; quem altera algo desfaz no fim
    private static String belezaId;
    private static String unhasId;
    private static String baratoId;
    private static String longeId;
    private static String inativoId;
    private static String tokenPerto;

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

    private JsonNode entrar(String conta) throws Exception {
        return esperar(200, "POST", "/api/auth/login", Map.of("email", conta, "senha", "Senha1234"), null);
    }

    private String estudio(String nome, String cpf, String tipo, String cidade, Double lat, Double lng,
                           List<Map<String, String>> servicos) throws Exception {
        String conta = nome.replaceAll("[^a-zA-Z]", "").toLowerCase() + "@example.com";
        Map<String, Object> corpo = new java.util.HashMap<>(Map.of(
                "nome", nome, "email", conta, "senha", "Senha1234", "telefone", "62999990001",
                "cpf", cpf, "dataNascimento", "1990-01-01", "especialidades", List.of("Beleza"),
                "servicos", servicos, "tipoEstabelecimento", tipo));
        corpo.put("endereco", Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                "bairro", "Centro", "cidade", cidade, "estado", "GO"));
        esperar(201, "POST", "/api/auth/registro/empresa", corpo, null);
        String id = entrar(conta).get("id").asText();
        jdbc.update("update empresa set latitude = ?, longitude = ? where id = ?::uuid", lat, lng, id);
        return id;
    }

    private Map<String, String> servico(String nome, String preco) {
        return Map.of("nome", nome, "preco", preco, "duracao", "60");
    }

    private void avaliar(String empresaId, int nota, int vezes) {
        String clienteId = jdbc.queryForObject("select id::text from cliente limit 1", String.class);
        for (int i = 0; i < vezes; i++) {
            UUID agendamento = UUID.randomUUID();
            jdbc.update("""
                    insert into agendamento (id, cliente_id, empresa_id, servico_id, data_hora_inicio, data_hora_fim,
                      status, data_criacao)
                    select ?, ?::uuid, ?::uuid, s.id, now(), now(), 'CONCLUIDO', now()
                    from servico s where s.empresa_id = ?::uuid limit 1
                    """, agendamento, clienteId, empresaId, empresaId);
            jdbc.update("""
                    insert into avaliacao (id, agendamento_id, cliente_id, empresa_id, tipo_avaliador, nota, data_criacao)
                    values (?, ?, ?::uuid, ?::uuid, 'CLIENTE', ?, now())
                    """, UUID.randomUUID(), agendamento, clienteId, empresaId, nota);
        }
    }

    @BeforeEach
    void preparar() throws Exception {
        if (tokenPerto != null) {
            return;
        }
        esperar(201, "POST", "/api/auth/registro/cliente", Map.of(
                "nome", "Perto", "email", "perto@example.com", "senha", "Senha1234", "telefone", "62999990000",
                "cpf", "52998224725", "dataNascimento", "1995-01-01", "genero", "FEMININO",
                "endereco", Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                        "bairro", "Centro", "cidade", "Goiania", "estado", "GO")), null);
        JsonNode perto = entrar("perto@example.com");
        tokenPerto = perto.get("token").asText();
        jdbc.update("update cliente set latitude = -16.68, longitude = -49.25 where id = ?::uuid", perto.get("id").asText());

        belezaId = estudio("Salão Beleza Plena", "11144477735", "ESTABELECIMENTO", "Goiânia", -16.69, -49.26,
                List.of(servico("Corte feminino", "80"), servico("Escova", "60")));
        unhasId = estudio("Studio Mãos", "39053344705", "AUTONOMA", "Goiânia", -16.75, -49.30,
                List.of(servico("Manicure", "40")));
        baratoId = estudio("Corte Certo", "86288366757", "AUTONOMA", "Anápolis", null, null,
                List.of(servico("Corte rápido", "20")));
        longeId = estudio("Longe 100%", "12345678909", "ESTABELECIMENTO", "Brasília", -15.79, -47.88,
                List.of(servico("Massagem", "150")));
        inativoId = estudio("Fechado", "98765432100", "AUTONOMA", "Goiânia", -16.68, -49.25,
                List.of(servico("Corte fechado", "10")));
        jdbc.update("update usuario set ativo = false where id = ?::uuid", inativoId);

        avaliar(belezaId, 5, 1);
        avaliar(unhasId, 3, 3);
    }

    private List<String> ids(String consulta, String token) throws Exception {
        JsonNode itens = esperar(200, "GET", BUSCA + consulta, null, token).get("itens");
        List<String> ids = new ArrayList<>();
        itens.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private static String q(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Sem filtros: traz os ativos, já com serviços, preço e avaliações, sem expor o documento")
    void semFiltros() throws Exception {
        JsonNode pagina = esperar(200, "GET", BUSCA, null, null);
        assertEquals(4, pagina.get("totalItens").asInt());
        assertEquals(1, pagina.get("totalPaginas").asInt());
        assertFalse(ids("", null).contains(inativoId));

        JsonNode beleza = null;
        for (JsonNode item : pagina.get("itens")) {
            if (item.get("id").asText().equals(belezaId)) {
                beleza = item;
            }
        }
        assertNotNull(beleza);
        assertEquals(60, beleza.get("menorPreco").asInt());
        assertEquals(2, beleza.get("servicos").size());
        assertEquals("Escova", beleza.get("servicos").get(0).get("nome").asText());
        assertEquals(5.0, beleza.get("mediaAvaliacoes").asDouble());
        assertEquals(1, beleza.get("quantidadeAvaliacoes").asInt());
        assertTrue(beleza.get("distanciaKm").isNull());
        assertFalse(beleza.has("cpf"));
        assertFalse(beleza.has("email"));
    }

    @Test
    @DisplayName("Texto: ignora acento e maiúscula, e procura também nos serviços")
    void texto() throws Exception {
        assertEquals(List.of(belezaId), ids("?texto=" + q("salao BELEZA"), null));
        assertEquals(List.of(unhasId), ids("?texto=" + q("MÃOS"), null));
        assertEquals(List.of(unhasId), ids("?texto=manicure", null));
        assertEquals(Set.of(belezaId, baratoId), new HashSet<>(ids("?texto=corte", null)));
        assertEquals(List.of(), ids("?texto=corte%20fechado", null));
    }

    @Test
    @DisplayName("Curingas e aspas são texto comum, não comandos")
    void caracteresEspeciais() throws Exception {
        assertEquals(List.of(longeId), ids("?texto=" + q("100%"), null));
        assertEquals(List.of(), ids("?texto=" + q("_____________________"), null));
        assertEquals(List.of(), ids("?texto=" + q("%%"), null));
        assertEquals(List.of(), ids("?texto=" + q("' OR 1=1 --"), null));
        assertEquals(List.of(), ids("?cidade=" + q("'; drop table empresa; --"), null));
        assertEquals(4, esperar(200, "GET", BUSCA, null, null).get("totalItens").asInt());
    }

    @Test
    @DisplayName("Filtra por cidade, tipo, serviço e categoria")
    void filtros() throws Exception {
        assertEquals(Set.of(belezaId, unhasId), new HashSet<>(ids("?cidade=" + q("goiania"), null)));
        assertEquals(Set.of(unhasId, baratoId), new HashSet<>(ids("?tipo=AUTONOMA", null)));
        assertEquals(List.of(longeId), ids("?servico=massagem", null));
        assertEquals(List.of(unhasId), ids("?categoria=UNHAS", null));
        assertEquals(Set.of(belezaId, baratoId), new HashSet<>(ids("?categoria=CABELO", null)));
        assertEquals(List.of(unhasId), ids("?tipo=AUTONOMA&cidade=" + q("Goiânia"), null));
    }

    @Test
    @DisplayName("Preço máximo vale para o serviço procurado e mantém quem não tem serviço")
    void preco() throws Exception {
        assertEquals(Set.of(belezaId, unhasId, baratoId), new HashSet<>(ids("?precoMaximo=60", null)));
        // O Beleza tem escova por 60 e o corte por 80: na categoria, a escova conta; no serviço, só o corte
        assertEquals(List.of(), ids("?servico=corte%20feminino&precoMaximo=70", null));
        assertEquals(List.of(belezaId), ids("?servico=corte%20feminino&precoMaximo=80", null));

        String sem = estudio("Sem Servico", "71428793860", "AUTONOMA", "Goiânia", null, null, List.of(servico("Temporario", "999")));
        jdbc.update("update servico set ativo = false where empresa_id = ?::uuid", sem);
        try {
            assertTrue(ids("?precoMaximo=60", null).contains(sem));
            assertFalse(ids("?categoria=OUTRO&precoMaximo=60", null).contains(sem));
        } finally {
            jdbc.update("update usuario set ativo = false where id = ?::uuid", sem);
        }
    }

    @Test
    @DisplayName("Nota mínima tira quem não tem avaliação, e a ordem por nota e por recomendação usa as avaliações")
    void notas() throws Exception {
        assertEquals(List.of(belezaId), ids("?notaMinima=4", null));
        assertEquals(Set.of(belezaId, unhasId), new HashSet<>(ids("?notaMinima=3", null)));

        List<String> porNota = ids("?ordenacao=AVALIADO", null);
        assertEquals(List.of(belezaId, unhasId), porNota.subList(0, 2));
        List<String> recomendados = ids("?ordenacao=RECOMENDADO", null);
        assertEquals(List.of(unhasId, belezaId), recomendados.subList(0, 2));
    }

    @Test
    @DisplayName("Ordena por preço com quem não tem preço no fim")
    void ordemPreco() throws Exception {
        assertEquals(List.of(baratoId, unhasId, belezaId, longeId), ids("?ordenacao=PRECO", null));
    }

    @Test
    @DisplayName("Com login de cliente: calcula a distância pelo perfil, filtra e ordena; sem coordenadas não some")
    void distancia() throws Exception {
        JsonNode pagina = esperar(200, "GET", BUSCA + "?ordenacao=PROXIMO", null, tokenPerto);
        List<String> ordem = new ArrayList<>();
        pagina.get("itens").forEach(item -> ordem.add(item.get("id").asText()));
        assertEquals(List.of(belezaId, unhasId, longeId, baratoId), ordem);

        JsonNode beleza = pagina.get("itens").get(0);
        assertTrue(beleza.get("distanciaKm").asDouble() > 1 && beleza.get("distanciaKm").asDouble() < 2,
                beleza.get("distanciaKm").asText());
        assertTrue(pagina.get("itens").get(3).get("distanciaKm").isNull());

        assertEquals(List.of(belezaId, unhasId, baratoId), ids("?distanciaMaximaKm=30&ordenacao=PROXIMO", tokenPerto));
        // Sem login não há de onde medir: o filtro não tira ninguém
        assertEquals(4, ids("?distanciaMaximaKm=30", null).size());
    }

    @Test
    @DisplayName("Token inválido na rota pública busca sem login, em vez de recusar")
    void tokenInvalido() throws Exception {
        assertEquals(4, ids("", "token-que-nao-existe").size());
    }

    @Test
    @DisplayName("Paginação: páginas não repetem nem perdem estúdios, e depois da última vem vazio com o total certo")
    void paginacao() throws Exception {
        Set<String> vistos = new HashSet<>();
        for (int pagina = 0; pagina < 2; pagina++) {
            JsonNode resposta = esperar(200, "GET", BUSCA + "?ordenacao=PRECO&tamanho=2&pagina=" + pagina, null, null);
            assertEquals(4, resposta.get("totalItens").asInt());
            assertEquals(2, resposta.get("totalPaginas").asInt());
            assertEquals(2, resposta.get("tamanho").asInt());
            assertEquals(pagina, resposta.get("pagina").asInt());
            resposta.get("itens").forEach(item -> assertTrue(vistos.add(item.get("id").asText())));
        }
        assertEquals(4, vistos.size());

        JsonNode depois = esperar(200, "GET", BUSCA + "?tamanho=2&pagina=9", null, null);
        assertEquals(0, depois.get("itens").size());
        assertEquals(4, depois.get("totalItens").asInt());
    }

    @Test
    @DisplayName("Parâmetros inválidos respondem 400")
    void invalidos() throws Exception {
        for (String consulta : List.of("?pagina=-1", "?tamanho=0", "?tamanho=51", "?precoMaximo=-1", "?notaMinima=6",
                "?distanciaMaximaKm=-1", "?categoria=NADA", "?tipo=NADA", "?ordenacao=NADA", "?pagina=abc",
                "?precoMaximo=abc", "?texto=" + "a".repeat(101))) {
            esperar(400, "GET", BUSCA + consulta, null, null);
        }
    }

    @Test
    @DisplayName("Serviços antigos, sem categoria, entram no filtro depois do preenchimento")
    void categoriaAntiga() throws Exception {
        jdbc.update("update servico set categoria = null where empresa_id = ?::uuid", longeId);
        assertEquals(List.of(), ids("?categoria=MASSAGEM", null));

        preenchimento.run(null);

        assertEquals(List.of(longeId), ids("?categoria=MASSAGEM", null));
    }
}
