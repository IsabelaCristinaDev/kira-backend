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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A conferência de horário livre e a gravação do agendamento são passos separados: só com o Postgres
 * de verdade e pedidos ao mesmo tempo dá para saber se dois deles conseguem pegar o mesmo horário.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AgendamentosSimultaneosComBancoRealTest {

    private static final String SENHA = "Senha1234";
    private static final List<String> CPFS = List.of("52998224725", "11144477735", "86288366757", "98765432100",
            "15350946056", "74682489070", "25829387000", "39053344705");
    /** Cada rodada usa um horário diferente: a disputa é repetida para não depender da sorte de uma só. */
    private static final List<Integer> HORAS_DAS_RODADAS = List.of(9, 12, 15);

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
    private final LocalDate dia = LocalDate.now(KiraTimeZone.DEFAULT).plusDays(3);

    private record Conta(String id, String token) {}

    // Criadas uma vez só: cadastrar 8 clientes por teste deixaria a classe lenta à toa
    private static List<Conta> clientes;
    private static Conta estudioSemFuncionarias;
    private static String servicoSemFuncionarias;
    private static Conta estudioComFuncionaria;
    private static String servicoComFuncionaria;
    private static String funcionaria;

    @BeforeEach
    void prepararContas() throws Exception {
        if (clientes != null) {
            return;
        }
        List<Conta> novas = new ArrayList<>();
        for (int i = 0; i < CPFS.size(); i++) {
            novas.add(novaCliente("cliente" + i, CPFS.get(i)));
        }
        clientes = novas;

        estudioSemFuncionarias = novoEstudio("salao", "11444777000161");
        servicoSemFuncionarias = primeiroServico(estudioSemFuncionarias);

        estudioComFuncionaria = novoEstudio("studio", "11222333000181");
        servicoComFuncionaria = primeiroServico(estudioComFuncionaria);
        funcionaria = esperar(201, "POST", "/api/empresas/" + estudioComFuncionaria.id() + "/funcionarias",
                Map.of("nome", "Ana", "comissaoPercentual", 10), estudioComFuncionaria.token()).get("id").asText();
    }

    @Test
    @DisplayName("Estúdio sem funcionárias: pedidos ao mesmo tempo para o mesmo horário geram um agendamento só")
    void semFuncionariasUmPorHorario() throws Exception {
        for (int hora : HORAS_DAS_RODADAS) {
            LocalDateTime inicio = dia.atTime(hora, 0);

            List<HttpResponse<String>> respostas =
                    disputar(estudioSemFuncionarias, servicoSemFuncionarias, null, inicio);

            conferirUmSoAgendamento(respostas, estudioSemFuncionarias, inicio,
                    "Já existe um agendamento neste horário para este estabelecimento");
        }
    }

    @Test
    @DisplayName("Mesma funcionária escolhida: pedidos ao mesmo tempo para o mesmo horário geram um agendamento só")
    void mesmaFuncionariaUmPorHorario() throws Exception {
        for (int hora : HORAS_DAS_RODADAS) {
            LocalDateTime inicio = dia.atTime(hora, 0);

            List<HttpResponse<String>> respostas =
                    disputar(estudioComFuncionaria, servicoComFuncionaria, funcionaria, inicio);

            conferirUmSoAgendamento(respostas, estudioComFuncionaria, inicio,
                    "A funcionária não está livre neste horário");
        }
    }

    @Test
    @DisplayName("Pedidos um depois do outro: sobreposto dá 400, encostado no limite é aceito e cancelado libera o horário")
    void emSequenciaAConferenciaFunciona() throws Exception {
        Conta primeira = clientes.get(0);
        Conta segunda = clientes.get(1);
        LocalDateTime inicio = dia.atTime(17, 0);

        String agendamento = agendar(primeira, inicio).get("id").asText();

        // O serviço dura 60 min: começar 30 min depois sobrepõe; começar quando o outro termina, não
        JsonNode sobreposto = esperar(400, "POST", "/api/agendamentos/cliente/" + segunda.id(),
                corpoDoAgendamento(inicio.plusMinutes(30)), segunda.token());
        assertEquals("Já existe um agendamento neste horário para este estabelecimento", sobreposto.get("mensagem").asText());
        agendar(segunda, inicio.plusMinutes(60));

        esperar(200, "PATCH", "/api/agendamentos/" + agendamento + "/cancelar", null, primeira.token());
        agendar(segunda, inicio);
    }

    private Map<String, Object> corpoDoAgendamento(LocalDateTime inicio) {
        return Map.of("empresaId", estudioSemFuncionarias.id(), "servicoId", servicoSemFuncionarias,
                "dataHoraInicio", inicio.toString(), "formaPagamento", "DINHEIRO");
    }

    private JsonNode agendar(Conta cliente, LocalDateTime inicio) throws Exception {
        return esperar(201, "POST", "/api/agendamentos/cliente/" + cliente.id(), corpoDoAgendamento(inicio),
                cliente.token());
    }

    /** Todas as clientes pedem o mesmo horário ao mesmo tempo. */
    private List<HttpResponse<String>> disputar(Conta estudio, String servicoId, String funcionariaId,
                                                LocalDateTime inicio) throws Exception {
        List<CompletableFuture<HttpResponse<String>>> pedidos = new ArrayList<>();
        for (Conta cliente : clientes) {
            Map<String, Object> corpo = new HashMap<>(Map.of("empresaId", estudio.id(), "servicoId", servicoId,
                    "dataHoraInicio", inicio.toString(), "formaPagamento", "DINHEIRO"));
            if (funcionariaId != null) {
                corpo.put("funcionariaId", funcionariaId);
            }
            pedidos.add(http.sendAsync(pedido("POST", "/api/agendamentos/cliente/" + cliente.id(), corpo,
                    cliente.token()), HttpResponse.BodyHandlers.ofString()));
        }
        List<HttpResponse<String>> respostas = new ArrayList<>();
        for (CompletableFuture<HttpResponse<String>> pedido : pedidos) {
            respostas.add(pedido.get());
        }
        return respostas;
    }

    private void conferirUmSoAgendamento(List<HttpResponse<String>> respostas, Conta estudio, LocalDateTime inicio,
                                         String mensagemDeOcupado) throws Exception {
        long criados = respostas.stream().filter(r -> r.statusCode() == 201).count();
        Integer gravados = jdbc.queryForObject(
                "select count(*) from agendamento where empresa_id = ?::uuid and data_hora_inicio = ?::timestamp"
                        + " and status <> 'CANCELADO'",
                Integer.class, estudio.id(), inicio.toString().replace('T', ' '));
        String resumo = "às " + inicio.toLocalTime() + ": " + criados + " pedidos aceitos e " + gravados
                + " agendamentos gravados no mesmo horário";

        assertEquals(1, criados, resumo);
        assertEquals(1, gravados, resumo);
        for (HttpResponse<String> resposta : respostas) {
            if (resposta.statusCode() != 201) {
                assertEquals(400, resposta.statusCode(), resposta.body());
                assertEquals(mensagemDeOcupado, json.readTree(resposta.body()).get("mensagem").asText());
            }
        }
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

    private JsonNode esperar(int status, String metodo, String caminho, Object corpo, String token) throws Exception {
        HttpResponse<String> resposta = http.send(pedido(metodo, caminho, corpo, token), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, resposta.statusCode(), metodo + " " + caminho + " -> " + resposta.body());
        return resposta.body().isBlank() ? json.nullNode() : json.readTree(resposta.body());
    }
}
