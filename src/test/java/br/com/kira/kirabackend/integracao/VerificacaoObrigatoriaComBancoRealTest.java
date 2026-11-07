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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Com VERIFICACAO_OBRIGATORIA=true, num Postgres de verdade: a vitrine, a busca e o agendamento só valem para
 * contas verificadas. Com a chave desligada (o padrão), o RotasComBancoRealTest agenda sem verificação nenhuma.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class VerificacaoObrigatoriaComBancoRealTest {

    @Container
    private static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BANCO::getJdbcUrl);
        registro.add("spring.datasource.username", BANCO::getUsername);
        registro.add("spring.datasource.password", BANCO::getPassword);
        registro.add("jwt.secret", () -> "segredo-usado-so-neste-teste-".repeat(4));
        registro.add("jwt.refresh-expiration", () -> "604800000");
        registro.add("kira.verificacao.obrigatoria", () -> "true");
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private JavaMailSender email;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final LocalDate adiante = LocalDate.now(KiraTimeZone.DEFAULT).plusDays(3);

    private static final String SENHA = "Senha1234";

    private record Conta(String id, String token) {}

    // Criadas uma vez só: os testes leem e só criam agendamentos em horários diferentes
    private static Conta verificada;
    private static Conta semVerificacao;
    private static Conta estudioVerificado;
    private static Conta estudioSemVerificacao;

    @BeforeEach
    void prepararContas() throws IOException, InterruptedException {
        if (verificada != null) {
            return;
        }
        verificada = cliente("vera", "39053344705");
        semVerificacao = cliente("nina", "12345678909");
        estudioVerificado = estudio("aprovado", "11444777000161");
        estudioSemVerificacao = estudio("pendente", "12345678000195");
        marcarVerificada(verificada, "CLIENTE");
        marcarVerificada(estudioVerificado, "EMPRESA");
    }

    @Test
    @DisplayName("Vitrine, busca por nome e pesquisa mostram só o estúdio verificado")
    void vitrineSoComVerificados() throws IOException, InterruptedException {
        assertEquals(List.of(estudioVerificado.id()), ids(chamar("GET", "/api/studios", null, null, 200)));
        assertEquals(List.of(estudioVerificado.id()), ids(chamar("GET", "/api/studios/busca?nome=a", null, null, 200)));
        assertEquals(List.of(estudioVerificado.id()),
                ids(chamar("GET", "/api/studios/pesquisa", null, verificada.token(), 200).get("itens")));
        assertEquals(1, chamar("GET", "/api/studios/pesquisa", null, null, 200).get("totalItens").asInt());
    }

    @Test
    @DisplayName("Cliente sem verificação recebe 403 com o código próprio e nada é gravado")
    void clienteSemVerificacao() throws IOException, InterruptedException {
        JsonNode erro = agendar(semVerificacao, estudioVerificado, 10, 403);

        assertEquals("VERIFICACAO_OBRIGATORIA", erro.get("erro").asText());
        assertEquals(0, jdbc.queryForObject("select count(*) from agendamento where cliente_id = ?::uuid",
                Integer.class, semVerificacao.id()));
    }

    @Test
    @DisplayName("Cliente verificada agenda com o estúdio verificado, mas não com o estúdio sem verificação")
    void clienteVerificada() throws IOException, InterruptedException {
        agendar(verificada, estudioVerificado, 11, 201);

        JsonNode erro = agendar(verificada, estudioSemVerificacao, 12, 400);
        assertEquals("REGRA_DE_NEGOCIO", erro.get("erro").asText());
    }

    @Test
    @DisplayName("A situação da verificação diz que ela é obrigatória")
    void situacaoDizQueEObrigatoria() throws IOException, InterruptedException {
        JsonNode situacao = chamar("GET", "/api/verificacao/me", null, semVerificacao.token(), 200);

        assertTrue(situacao.get("required").asBoolean());
        assertTrue(situacao.get("status").isNull());
    }

    // ---- apoio ----

    private void marcarVerificada(Conta conta, String tipo) {
        jdbc.update("""
                insert into verificacao_identidade (id, usuario_id, tipo_usuario, tipo_documento, status, motivos,
                  versao_consentimento, consentimento_aceito_em, data_criacao, data_atualizacao)
                values (?, ?::uuid, ?, 'CNH', 'VERIFIED', '[]'::jsonb, 'v1', now(), now(), now())
                """, UUID.randomUUID(), conta.id(), tipo);
    }

    private JsonNode agendar(Conta cliente, Conta estudio, int hora, int esperado)
            throws IOException, InterruptedException {
        String servico = chamar("GET", "/api/studios/" + estudio.id() + "/servicos", null, null, 200)
                .get(0).get("id").asText();
        return chamar("POST", "/api/agendamentos/cliente/" + cliente.id(), Map.of(
                "empresaId", estudio.id(), "servicoId", servico,
                "dataHoraInicio", adiante + "T%02d:00:00".formatted(hora)), cliente.token(), esperado);
    }

    private static List<String> ids(JsonNode lista) {
        List<String> ids = new ArrayList<>();
        lista.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }

    private Conta cliente(String nome, String cpf) throws IOException, InterruptedException {
        chamar("POST", "/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO", "endereco", endereco()), null, 201);
        return entrar(nome);
    }

    private Conta estudio(String nome, String cnpj) throws IOException, InterruptedException {
        chamar("POST", "/api/auth/registro/empresa", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990001",
                "cnpj", cnpj, "descricao", "Cortes", "endereco", endereco(),
                "tipoEstabelecimento", "ESTABELECIMENTO",
                "servicos", List.of(Map.of("nome", "Corte", "preco", "80", "duracao", "60"))), null, 201);
        Conta conta = entrar(nome);
        List<Map<String, String>> semana = List.of("SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO", "DOMINGO")
                .stream().map(d -> Map.of("diaSemana", d, "horaInicio", "08:00", "horaFim", "20:00")).toList();
        chamar("PUT", "/api/empresas/" + conta.id() + "/disponibilidade", Map.of("dias", semana), conta.token(), 200);
        return conta;
    }

    private Conta entrar(String nome) throws IOException, InterruptedException {
        JsonNode login = chamar("POST", "/api/auth/login",
                Map.of("email", nome + "@example.com", "senha", SENHA), null, 200);
        return new Conta(login.get("id").asText(), login.get("token").asText());
    }

    private static Map<String, Object> endereco() {
        return Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                "bairro", "Centro", "cidade", "Goiania", "estado", "GO");
    }

    private JsonNode chamar(String metodo, String caminho, Object corpo, String token, int esperado)
            throws IOException, InterruptedException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo)));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resposta = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(esperado, resposta.statusCode(), metodo + " " + caminho + " -> " + resposta.body());
        return resposta.body().isBlank() ? json.nullNode() : json.readTree(resposta.body());
    }
}
