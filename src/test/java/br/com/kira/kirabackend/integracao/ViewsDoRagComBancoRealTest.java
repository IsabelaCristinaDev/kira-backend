package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.KiraBackendApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acesso de leitura do RAG (docker/postgres/rag.sql) no Postgres com pgvector montado pelo Dockerfile do
 * projeto: o que o kira_rag enxerga pelas views, o que ele não enxerga, e a API subindo de novo, com o
 * ddl-auto, depois de as views existirem.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class ViewsDoRagComBancoRealTest {

    private static final String SENHA = "Senha1234";
    /** Só para o teste entrar como kira_rag: o script cria o usuário sem senha. */
    private static final String SENHA_DO_RAG = "senha-usada-so-neste-teste";
    private static final Path PASTA_DO_POSTGRES = Path.of("docker", "postgres");

    @Container
    private static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>(
            DockerImageName.parse(imagemComPgvector()).asCompatibleSubstituteFor("postgres"));

    /**
     * Monta a mesma imagem do docker-compose (Postgres 16 Alpine com pgvector). O nome fixo e o
     * deleteOnExit desligado deixam o cache do Docker valer de uma rodada para a outra.
     */
    private static String imagemComPgvector() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return "postgres:16-alpine"; // A classe é pulada sem Docker; o nome só evita erro ao carregá-la
        }
        return new ImageFromDockerfile("kira-postgres-teste:16-pgvector", false)
                .withFileFromPath(".", PASTA_DO_POSTGRES)
                .get();
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BANCO::getJdbcUrl);
        registro.add("spring.datasource.username", BANCO::getUsername);
        registro.add("spring.datasource.password", BANCO::getPassword);
        registro.add("jwt.secret", ViewsDoRagComBancoRealTest::segredoJwt);
        registro.add("jwt.refresh-expiration", () -> "604800000");
    }

    private static String segredoJwt() {
        return "segredo-usado-so-neste-teste-".repeat(4);
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

    private static Conta estudioVisivel;
    private static String servicoAtivo;
    private static String servicoDesativado;
    private static Conta estudioInativo;
    private static String servicoDoInativo;

    /** Contas criadas pela API, estúdio desativado e o rag.sql aplicado, uma vez só para a classe. */
    @BeforeEach
    void prepararBanco() throws Exception {
        if (estudioVisivel != null) {
            return;
        }
        estudioVisivel = novoEstudio("visivel", "11444777000161");
        servicoAtivo = primeiroServico(estudioVisivel);
        servicoDesativado = esperar(201, "POST", "/api/empresas/" + estudioVisivel.id() + "/servicos",
                Map.of("nome", "Escova", "duracaoMinutos", 30, "preco", 40), estudioVisivel.token()).get("id").asText();
        esperar(204, "DELETE", "/api/empresas/" + estudioVisivel.id() + "/servicos/" + servicoDesativado, null,
                estudioVisivel.token());

        estudioInativo = novoEstudio("inativo", "11222333000181");
        servicoDoInativo = primeiroServico(estudioInativo);
        jdbc.update("update usuario set ativo = false where id = ?::uuid", estudioInativo.id());

        aplicarScriptDoRag();
        jdbc.execute("alter role kira_rag password '" + SENHA_DO_RAG + "'");
    }

    @Test
    @DisplayName("kira_rag lê as views de estúdio, horário e serviço com os dados do estúdio ativo")
    void kiraRagLeAsViews() throws Exception {
        try (Connection rag = conectarComoKiraRag()) {
            List<String> estudio = linhas(rag, "select nome || '|' || bairro || '|' || descricao"
                    + " from kira_leitura.estudio where id = '" + estudioVisivel.id() + "'");
            assertEquals(List.of("visivel|Centro|Cortes"), estudio);

            List<String> horarios = linhas(rag, "select dia_semana || ' ' || hora_inicio || '-' || hora_fim"
                    + " from kira_leitura.horario_estudio where estudio_id = '" + estudioVisivel.id() + "'");
            assertEquals(7, horarios.size(), horarios.toString());
            assertTrue(horarios.contains("SEGUNDA 08:00:00-20:00:00"), horarios.toString());

            List<String> servicos = linhas(rag, "select id || '|' || nome || '|' || preco || '|' || duracao_minutos"
                    + " from kira_leitura.servico where estudio_id = '" + estudioVisivel.id() + "'");
            assertEquals(List.of(servicoAtivo + "|Corte|80.00|60"), servicos);
        }
    }

    @Test
    @DisplayName("Estúdio inativo e serviço desativado não aparecem para o kira_rag")
    void inativosNaoAparecem() throws Exception {
        try (Connection rag = conectarComoKiraRag()) {
            assertEquals(List.of(), linhas(rag, "select id::text from kira_leitura.estudio where id = '"
                    + estudioInativo.id() + "'"));
            assertEquals(List.of(), linhas(rag, "select id::text from kira_leitura.horario_estudio where estudio_id = '"
                    + estudioInativo.id() + "'"));
            List<String> servicos = linhas(rag, "select id::text from kira_leitura.servico");
            assertFalse(servicos.contains(servicoDoInativo), servicos.toString());
            assertFalse(servicos.contains(servicoDesativado), servicos.toString());
            assertTrue(servicos.contains(servicoAtivo), servicos.toString());
        }
    }

    @Test
    @DisplayName("kira_rag não lê as tabelas da API, só as views")
    void kiraRagNaoLeAsTabelas() throws Exception {
        try (Connection rag = conectarComoKiraRag()) {
            for (String tabela : List.of("public.usuario", "public.empresa", "public.servico",
                    "public.disponibilidade_estudio", "public.agendamento")) {
                SQLException erro = assertThrows(SQLException.class,
                        () -> linhas(rag, "select count(*)::text from " + tabela), tabela);
                assertEquals("42501", erro.getSQLState(), tabela + ": " + erro.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Com as views criadas, a API sobe de novo e o ddl-auto continua ajustando as tabelas")
    void apiSobeDeNovoComAsViews() throws Exception {
        // Com views diretas sobre as tabelas, o Postgres barrava mudar uma coluna usada por elas
        jdbc.execute("alter table servico alter column nome type varchar(200)");
        jdbc.execute("alter table servico alter column nome type varchar(150)");
        // Coluna que o Hibernate precisa recriar na subida, numa tabela com view de mesmo nome em kira_leitura
        jdbc.execute("alter table servico drop column foto_url");

        try (ConfigurableApplicationContext outraSubida = new SpringApplicationBuilder(KiraBackendApplication.class)
                .run("--server.port=0",
                        "--spring.datasource.url=" + BANCO.getJdbcUrl(),
                        "--spring.datasource.username=" + BANCO.getUsername(),
                        "--spring.datasource.password=" + BANCO.getPassword(),
                        "--jwt.secret=" + segredoJwt(),
                        "--jwt.refresh-expiration=604800000")) {
            assertTrue(outraSubida.isActive());
        }

        assertEquals(1, jdbc.queryForObject("select count(*) from information_schema.columns"
                + " where table_schema = 'public' and table_name = 'servico' and column_name = 'foto_url'",
                Integer.class));
        try (Connection rag = conectarComoKiraRag()) {
            assertTrue(linhas(rag, "select id::text from kira_leitura.servico").contains(servicoAtivo));
        }
    }

    // ---------- banco ----------

    private void aplicarScriptDoRag() throws Exception {
        String script = Files.readString(PASTA_DO_POSTGRES.resolve("rag.sql"), StandardCharsets.UTF_8);
        // Uma transação só, como no README (psql -1)
        try (Connection dono = DriverManager.getConnection(BANCO.getJdbcUrl(), BANCO.getUsername(), BANCO.getPassword());
             Statement comando = dono.createStatement()) {
            dono.setAutoCommit(false);
            comando.execute(script);
            dono.commit();
        }
    }

    private static Connection conectarComoKiraRag() throws SQLException {
        return DriverManager.getConnection(BANCO.getJdbcUrl(), "kira_rag", SENHA_DO_RAG);
    }

    private static List<String> linhas(Connection conexao, String sql) throws SQLException {
        List<String> linhas = new ArrayList<>();
        try (Statement comando = conexao.createStatement(); ResultSet resultado = comando.executeQuery(sql)) {
            while (resultado.next()) {
                linhas.add(resultado.getString(1));
            }
        }
        return linhas;
    }

    // ---------- contas ----------

    private Conta novoEstudio(String nome, String cnpj) throws Exception {
        esperar(201, "POST", "/api/auth/registro/empresa", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990001",
                "cnpj", cnpj, "descricao", "Cortes", "endereco", endereco(),
                "tipoEstabelecimento", "ESTABELECIMENTO",
                "servicos", List.of(Map.of("nome", "Corte", "preco", "80", "duracao", "60"))), null);
        Conta estudio = entrar(nome);
        List<Map<String, String>> semana = List.of("SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO", "DOMINGO")
                .stream().map(d -> Map.of("diaSemana", d, "horaInicio", "08:00", "horaFim", "20:00")).toList();
        esperar(200, "PUT", "/api/empresas/" + estudio.id() + "/disponibilidade", Map.of("dias", semana), estudio.token());
        return estudio;
    }

    private String primeiroServico(Conta estudio) throws Exception {
        return esperar(200, "GET", "/api/empresas/" + estudio.id() + "/servicos", null, estudio.token())
                .get(0).get("id").asText();
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
