package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.util.KiraTimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direitos do titular (LGPD): exportar os próprios dados e excluir a conta, com o banco de verdade. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class PrivacidadeComBancoRealTest {

    private static final String DADOS = "/api/perfil/dados";
    private static final String PERFIL = "/api/perfil";
    private static final String SENHA = "Senha1234";
    private static final String CONTA_EXCLUIDA = "Conta excluída";
    // Um ponto válido da curva P-256 e 16 bytes, que é o formato exigido
    private static final String P256DH =
            "BGsX0fLhLEJH-Lzm5WOkQPJ3A32BLeszoPShOUXYmMKWT-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU";
    private static final String AUTH = "YXV0aC1kZS10ZXN0ZS0xNg";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    @Container
    private static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    private static Path pastaDeArquivos;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BANCO::getJdbcUrl);
        registro.add("spring.datasource.username", BANCO::getUsername);
        registro.add("spring.datasource.password", BANCO::getPassword);
        registro.add("jwt.secret", () -> "segredo-usado-so-neste-teste-".repeat(4));
        registro.add("jwt.refresh-expiration", () -> "604800000");
        registro.add("kira.arquivos.pasta", () -> pastaDeArquivos.toString());
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private JavaMailSender email;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final LocalDate hoje = LocalDate.now(KiraTimeZone.DEFAULT);

    private record Conta(String id, String token) {}

    // ---------- auxiliares ----------

    private HttpResponse<String> enviar(String metodo, String caminho, Object corpo, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode chamar(String metodo, String caminho, Object corpo, String token, int esperado)
            throws IOException, InterruptedException {
        HttpResponse<String> resposta = enviar(metodo, caminho, corpo, token);
        assertEquals(esperado, resposta.statusCode(), metodo + " " + caminho + " -> " + resposta.body());
        return resposta.body().isBlank() ? json.nullNode() : json.readTree(resposta.body());
    }

    private JsonNode get(String caminho, String token) throws IOException, InterruptedException {
        return chamar("GET", caminho, null, token, 200);
    }

    private String enviarFoto(String caminho, String token, int esperado) throws IOException, InterruptedException {
        String limite = "limite-do-teste";
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        corpo.write(("--" + limite + "\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"foto.png\""
                + "\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        corpo.write(PNG);
        corpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()))
                .build();
        HttpResponse<String> resposta = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(esperado, resposta.statusCode(), resposta.body());
        JsonNode foto = json.readTree(resposta.body());
        return foto.has("fotoUrl") ? foto.get("fotoUrl").asText() : foto.get("url").asText();
    }

    private int abrir(String caminho) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private Map<String, Object> endereco() {
        return Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                "bairro", "Centro", "cidade", "Goiania", "estado", "GO");
    }

    private void cadastrarCliente(String nome, String cpf, int esperado) throws IOException, InterruptedException {
        chamar("POST", "/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO", "endereco", endereco()),
                null, esperado);
    }

    private void cadastrarEstudio(String nome, String cnpj, int esperado) throws IOException, InterruptedException {
        chamar("POST", "/api/auth/registro/empresa", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990001",
                "cnpj", cnpj, "descricao", "Cortes", "endereco", endereco(),
                "tipoEstabelecimento", "ESTABELECIMENTO", "especialidades", List.of("Cabelo"),
                "servicos", List.of(Map.of("nome", "Corte", "preco", "80", "duracao", "60"))), null, esperado);
    }

    private Conta entrar(String nome) throws IOException, InterruptedException {
        JsonNode login = chamar("POST", "/api/auth/login", Map.of("email", nome + "@example.com", "senha", SENHA), null, 200);
        return new Conta(login.get("id").asText(), login.get("token").asText());
    }

    private Conta novaCliente(String nome, String cpf) throws IOException, InterruptedException {
        cadastrarCliente(nome, cpf, 201);
        return entrar(nome);
    }

    private Conta novoEstudio(String nome, String cnpj) throws IOException, InterruptedException {
        cadastrarEstudio(nome, cnpj, 201);
        Conta estudio = entrar(nome);
        List<Map<String, String>> semana = List.of("SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO", "DOMINGO")
                .stream().map(d -> Map.of("diaSemana", d, "horaInicio", "08:00", "horaFim", "20:00")).toList();
        chamar("PUT", "/api/empresas/" + estudio.id() + "/disponibilidade", Map.of("dias", semana), estudio.token(), 200);
        return estudio;
    }

    private String servico(Conta estudio) throws IOException, InterruptedException {
        return get("/api/empresas/" + estudio.id() + "/servicos", estudio.token()).get(0).get("id").asText();
    }

    private String agendar(Conta cliente, Conta estudio, String dataHora) throws IOException, InterruptedException {
        Map<String, Object> corpo = new HashMap<>(Map.of("empresaId", estudio.id(), "servicoId", servico(estudio),
                "dataHoraInicio", dataHora, "formaPagamento", "DINHEIRO"));
        return chamar("POST", "/api/agendamentos/cliente/" + cliente.id(), corpo, cliente.token(), 201)
                .get("id").asText();
    }

    /** Atendimento que já aconteceu: agenda no futuro, leva para o passado e conclui. */
    private String atendimentoConcluido(Conta cliente, Conta estudio) throws IOException, InterruptedException {
        String id = agendar(cliente, estudio, hoje.plusDays(2) + "T09:00:00");
        LocalDate dia = hoje.minusDays(3);
        jdbc.update("update agendamento set data_hora_inicio = ?::timestamp, data_hora_fim = ?::timestamp where id = ?::uuid",
                dia + " 10:00:00", dia + " 11:00:00", id);
        chamar("PATCH", "/api/agendamentos/" + id + "/concluir", null, estudio.token(), 200);
        return id;
    }

    private void registrarNavegador(Conta conta, String endereco) throws IOException, InterruptedException {
        chamar("POST", "/api/notificacoes/dispositivos/web",
                Map.of("endpoint", endereco, "p256dh", P256DH, "auth", AUTH), conta.token(), 204);
    }

    private String status(String agendamentoId) {
        return jdbc.queryForObject("select status from agendamento where id = ?::uuid", String.class, agendamentoId);
    }

    private int contar(String tabela, String coluna, String id) {
        return jdbc.queryForObject("select count(*) from " + tabela + " where " + coluna + " = ?::uuid",
                Integer.class, id);
    }

    private static Map<String, String> senha(String valor) {
        return Map.of("senha", valor);
    }

    // ---------- exportação ----------

    @Test
    @DisplayName("Exportar (cliente): devolve todos os dados da conta, sem senha, hash, token nem chaves")
    void exportarCliente() throws Exception {
        Conta estudio = novoEstudio("lumiere", "11444777000161");
        Conta ana = novaCliente("ana", "52998224725");
        String foto = enviarFoto("/api/perfil/foto", ana.token(), 200);
        String concluido = atendimentoConcluido(ana, estudio);
        chamar("POST", "/api/avaliacoes/cliente",
                Map.of("agendamentoId", concluido, "nota", 5, "comentario", "Ótimo"), ana.token(), 201);
        chamar("POST", "/api/avaliacoes/empresa", Map.of("agendamentoId", concluido, "nota", 4), estudio.token(), 201);
        chamar("POST", "/api/mensagens/remetente/" + ana.id(),
                Map.of("agendamentoId", concluido, "conteudo", "Obrigada!"), ana.token(), 201);
        chamar("PUT", "/api/favoritas/" + estudio.id(), null, ana.token(), 204);
        chamar("POST", "/api/contatos-emergencia",
                Map.of("nome", "Rosa", "telefone", "62988887777", "parentesco", "Mãe"), ana.token(), 201);
        chamar("POST", "/api/provador/consentimento", Map.of("versao", "v1"), ana.token(), 200);
        jdbc.update("""
                insert into verificacao_identidade (id, usuario_id, tipo_usuario, tipo_documento, status, motivos,
                  similaridade, confianca_ocr, request_id, versao_consentimento, consentimento_aceito_em,
                  data_criacao, data_atualizacao)
                values (?::uuid, ?::uuid, 'CLIENTE', 'CNH', 'VERIFIED', '[]'::jsonb, 0.9876, 0.5432, 'req-interno-1',
                  'v1', now(), now(), now())
                """, UUID.randomUUID().toString(), ana.id());
        String navegador = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        registrarNavegador(ana, navegador);
        chamar("PUT", "/api/clientes/" + ana.id() + "/preferencias",
                Map.of("categorias", List.of("UNHAS", "CILIOS")), ana.token(), 200);

        chamar("GET", DADOS, null, null, 401);
        HttpResponse<String> resposta = enviar("GET", DADOS, null, ana.token());
        assertEquals(200, resposta.statusCode(), resposta.body());
        JsonNode dados = json.readTree(resposta.body());

        assertEquals("CLIENTE", dados.get("tipoConta").asText());
        JsonNode conta = dados.get("conta");
        assertEquals("ana@example.com", conta.get("email").asText());
        assertEquals("52998224725", conta.get("cpf").asText());
        assertEquals("62999990000", conta.get("telefone").asText());
        assertEquals("Goiania", conta.get("endereco").get("cidade").asText());
        assertTrue(conta.has("latitude"));
        assertEquals("[\"CILIOS\",\"UNHAS\"]", conta.get("preferencias").toString());
        assertEquals(foto, conta.get("fotoUrl").asText());
        assertEquals(1, dados.get("agendamentos").size());
        assertEquals("lumiere", dados.get("agendamentos").get(0).get("nomeEmpresa").asText());
        assertEquals(5, dados.get("avaliacoesFeitas").get(0).get("nota").asInt());
        assertEquals(4, dados.get("avaliacoesRecebidas").get(0).get("nota").asInt());
        assertEquals("Obrigada!", dados.get("mensagensEnviadas").get(0).get("conteudo").asText());
        assertEquals(estudio.id(), dados.get("favoritas").get(0).get("id").asText());
        assertEquals("Rosa", dados.get("contatosEmergencia").get(0).get("nome").asText());
        assertEquals("v1", dados.get("consentimentos").get("provador").get("versao").asText());
        JsonNode verificacao = dados.get("consentimentos").get("verificacoes").get(0);
        assertEquals("VERIFIED", verificacao.get("status").asText());
        assertEquals("fcm.googleapis.com", dados.get("dispositivosNotificacao").get(0).get("servico").asText());
        assertEquals(foto, dados.get("fotos").get(0).asText());
        assertTrue(dados.get("estudio").isNull());

        String hash = jdbc.queryForObject("select senha_hash from usuario where id = ?::uuid", String.class, ana.id());
        String texto = resposta.body();
        assertFalse(texto.contains(hash));
        assertFalse(texto.toLowerCase().contains("senha"));
        assertFalse(texto.toLowerCase().contains("token"));
        assertFalse(texto.contains(P256DH));
        assertFalse(texto.contains(AUTH));
        assertFalse(texto.contains(navegador));
        assertFalse(texto.contains("0.9876"));
        assertFalse(texto.contains("req-interno-1"));
    }

    @Test
    @DisplayName("Exportar (estúdio): inclui serviços, funcionárias, portfólio e horário")
    void exportarEstudio() throws Exception {
        Conta estudio = novoEstudio("solar", "12345678000195");
        chamar("POST", "/api/empresas/" + estudio.id() + "/funcionarias",
                Map.of("nome", "Carla", "comissaoPercentual", 10), estudio.token(), 201);
        String foto = enviarFoto("/api/empresas/" + estudio.id() + "/portfolio", estudio.token(), 201);

        JsonNode dados = get(DADOS, estudio.token());

        assertEquals("EMPRESA", dados.get("tipoConta").asText());
        assertEquals("12345678000195", dados.get("conta").get("cnpj").asText());
        assertEquals("Cabelo", dados.get("conta").get("especialidades").get(0).asText());
        JsonNode dadosDoEstudio = dados.get("estudio");
        assertEquals("Corte", dadosDoEstudio.get("servicos").get(0).get("nome").asText());
        assertEquals("Carla", dadosDoEstudio.get("funcionarias").get(0).get("nome").asText());
        assertEquals(foto, dadosDoEstudio.get("portfolio").get(0).get("url").asText());
        assertEquals(7, dadosDoEstudio.get("horario").size());
        assertTrue(dados.get("fotos").toString().contains(foto));
    }

    // ---------- exclusão ----------

    @Test
    @DisplayName("Excluir: sem senha ou com a senha errada responde 400 e a conta continua igual")
    void excluirComSenhaErrada() throws Exception {
        Conta duda = novaCliente("duda", "12345678909");

        chamar("DELETE", PERFIL, null, null, 401);
        chamar("DELETE", PERFIL, senha("Errada123"), duda.token(), 400);
        chamar("DELETE", PERFIL, Map.of(), duda.token(), 400);
        chamar("DELETE", PERFIL, null, duda.token(), 400);

        entrar("duda");
        assertEquals("duda", get(DADOS, duda.token()).get("conta").get("nome").asText());
    }

    @Test
    @DisplayName("Excluir (cliente): anonimiza, cancela os futuros, mantém o histórico e libera e-mail e CPF")
    void excluirCliente() throws Exception {
        Conta estudio = novoEstudio("bellaire", "11222333000181");
        Conta bia = novaCliente("bia", "11144477735");
        String foto = enviarFoto("/api/perfil/foto", bia.token(), 200);
        assertEquals(200, abrir(foto));
        String passado = atendimentoConcluido(bia, estudio);
        String futuro = agendar(bia, estudio, hoje.plusDays(5) + "T10:00:00");
        chamar("PATCH", "/api/agendamentos/" + futuro + "/confirmar", null, estudio.token(), 200);
        chamar("POST", "/api/mensagens/remetente/" + bia.id(),
                Map.of("agendamentoId", passado, "conteudo", "Até a próxima"), bia.token(), 201);
        chamar("POST", "/api/contatos-emergencia", Map.of("nome", "Rosa", "telefone", "62988887777"), bia.token(), 201);
        chamar("PUT", "/api/favoritas/" + estudio.id(), null, bia.token(), 204);
        chamar("POST", "/api/provador/consentimento", Map.of("versao", "v1"), bia.token(), 200);
        registrarNavegador(bia, "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID());
        chamar("PUT", "/api/clientes/" + bia.id() + "/preferencias",
                Map.of("categorias", List.of("UNHAS")), bia.token(), 200);

        chamar("DELETE", PERFIL, senha(SENHA), bia.token(), 204);

        // Não entra mais, nem com o token antigo
        chamar("POST", "/api/auth/login", Map.of("email", "bia@example.com", "senha", SENHA), null, 401);
        chamar("GET", DADOS, null, bia.token(), 401);

        Map<String, Object> linha = jdbc.queryForMap("""
                select u.nome, u.email, u.telefone, u.foto_url, u.ativo, c.cpf, c.endereco_cidade, c.latitude,
                  c.data_nascimento
                from usuario u join cliente c on c.id = u.id where u.id = ?::uuid
                """, bia.id());
        assertEquals(CONTA_EXCLUIDA, linha.get("nome"));
        assertFalse(linha.get("email").toString().contains("@"));
        assertNull(linha.get("telefone"));
        assertNull(linha.get("foto_url"));
        assertEquals(false, linha.get("ativo"));
        assertFalse(linha.get("cpf").toString().contains("11144477735"));
        assertNull(linha.get("endereco_cidade"));
        assertNull(linha.get("latitude"));
        assertNull(linha.get("data_nascimento"));
        assertEquals(404, abrir(foto));

        for (String tabela : List.of("contato_emergencia", "favorita", "cliente_preferencia")) {
            assertEquals(0, contar(tabela, "cliente_id", bia.id()), tabela);
        }
        for (String tabela : List.of("dispositivo_notificacao", "refresh_token", "consentimento_provador")) {
            assertEquals(0, contar(tabela, "usuario_id", bia.id()), tabela);
        }

        // O estúdio continua vendo o histórico, com o nome trocado, e a mensagem continua na conversa
        assertEquals("CONCLUIDO", status(passado));
        assertEquals("CANCELADO", status(futuro));
        for (JsonNode agendamento : get("/api/agendamentos/empresa/" + estudio.id(), estudio.token())) {
            assertEquals(CONTA_EXCLUIDA, agendamento.get("nomeCliente").asText());
        }
        JsonNode conversa = get("/api/mensagens/agendamento/" + passado, estudio.token());
        assertEquals(CONTA_EXCLUIDA, conversa.get(0).get("nomeRemetente").asText());
        assertEquals("Até a próxima", conversa.get(0).get("conteudo").asText());

        // E-mail e CPF ficam livres para um novo cadastro
        Conta nova = novaCliente("bia", "11144477735");
        assertEquals("bia", get(DADOS, nova.token()).get("conta").get("nome").asText());
        assertTrue(get(DADOS, nova.token()).get("agendamentos").isEmpty());
    }

    @Test
    @DisplayName("Excluir (estúdio): some da vitrine, cancela os futuros, apaga o portfólio e libera e-mail e CNPJ")
    void excluirEstudio() throws Exception {
        Conta estudio = novoEstudio("aurora", "45723174000110");
        String foto = enviarFoto("/api/empresas/" + estudio.id() + "/portfolio", estudio.token(), 201);
        Conta carol = novaCliente("carol", "39053344705");
        String futuro = agendar(carol, estudio, hoje.plusDays(4) + "T15:00:00");
        chamar("PUT", "/api/favoritas/" + estudio.id(), null, carol.token(), 204);

        chamar("DELETE", PERFIL, senha("Errada123"), estudio.token(), 400);
        chamar("DELETE", PERFIL, senha(SENHA), estudio.token(), 204);

        chamar("GET", "/api/studios/" + estudio.id(), null, null, 404);
        chamar("POST", "/api/auth/login", Map.of("email", "aurora@example.com", "senha", SENHA), null, 401);
        assertEquals("CANCELADO", status(futuro));
        assertEquals(CONTA_EXCLUIDA,
                get("/api/agendamentos/cliente/" + carol.id(), carol.token()).get(0).get("nomeEmpresa").asText());
        assertEquals(0, contar("foto_portfolio", "empresa_id", estudio.id()));
        assertEquals(0, contar("favorita", "empresa_id", estudio.id()));
        assertEquals(404, abrir(foto));
        assertNull(jdbc.queryForObject("select cnpj from empresa where id = ?::uuid", String.class, estudio.id()));

        cadastrarEstudio("aurora", "45723174000110", 201);
    }
}
