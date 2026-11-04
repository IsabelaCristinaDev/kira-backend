package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.job.LembreteAgendamentoJob;
import br.com.kira.kirabackend.util.KiraTimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Percorre as rotas da API com um Postgres de verdade, descartável, e o e-mail simulado.
 * Os testes unitários usam dados simulados e não pegam erro de transação (relações LAZY sem sessão aberta).
 * Sem Docker na máquina, este teste é ignorado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class RotasComBancoRealTest {

    @Container
    private static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BANCO::getJdbcUrl);
        registro.add("spring.datasource.username", BANCO::getUsername);
        registro.add("spring.datasource.password", BANCO::getPassword);
        registro.add("jwt.secret", () -> "segredo-usado-so-neste-teste-".repeat(4));
        registro.add("jwt.refresh-expiration", () -> "604800000");
        registro.add("LEMBRETE_ANTECEDENCIA_HORAS", () -> "48");
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LembreteAgendamentoJob lembreteJob;

    @MockBean
    private JavaMailSender email;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private static final String SENHA = "Senha1234";
    private final LocalDate hoje = LocalDate.now(KiraTimeZone.DEFAULT);

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

    private JsonNode get(String caminho, String token) throws IOException, InterruptedException {
        return chamar("GET", caminho, null, token, 200);
    }

    private Map<String, Object> endereco() {
        return Map.of("cep", "74000000", "logradouro", "Rua A", "numero", "1",
                "bairro", "Centro", "cidade", "Goiania", "estado", "GO");
    }

    private void cadastrarCliente(String nome, String cpf) throws IOException, InterruptedException {
        chamar("POST", "/api/auth/registro/cliente", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990000",
                "cpf", cpf, "dataNascimento", "1995-01-01", "genero", "FEMININO", "endereco", endereco()), null, 201);
    }

    private JsonNode entrar(String nome) throws IOException, InterruptedException {
        return chamar("POST", "/api/auth/login", Map.of("email", nome + "@example.com", "senha", SENHA), null, 200);
    }

    private String agendar(String clienteId, String tokenCliente, String empresaId, String servicoId,
                           String funcionariaId, String dataHora) throws IOException, InterruptedException {
        Map<String, Object> corpo = new HashMap<>(Map.of("empresaId", empresaId, "servicoId", servicoId,
                "dataHoraInicio", dataHora, "formaPagamento", "DINHEIRO"));
        if (funcionariaId != null) {
            corpo.put("funcionariaId", funcionariaId);
        }
        JsonNode criado = chamar("POST", "/api/agendamentos/cliente/" + clienteId, corpo, tokenCliente, 201);
        return criado.get("id").asText();
    }

    @Test
    @DisplayName("Todas as rotas respondem certo com banco de verdade, inclusive com dados nas relações")
    void percorreAsRotas() throws IOException, InterruptedException {
        cadastrarCliente("ana", "52998224725");
        cadastrarCliente("bia", "11144477735");
        chamar("POST", "/api/auth/registro/empresa", Map.of(
                "nome", "studio", "email", "studio@example.com", "senha", SENHA, "telefone", "62999990001",
                "cnpj", "11222333000181", "descricao", "Cortes", "endereco", endereco(),
                "tipoEstabelecimento", "ESTABELECIMENTO",
                "servicos", List.of(Map.of("nome", "Corte", "preco", "80", "duracao", "60"))), null, 201);

        JsonNode loginAna = entrar("ana");
        JsonNode loginEstudio = entrar("studio");
        String ana = loginAna.get("token").asText();
        String anaId = loginAna.get("id").asText();
        String bia = entrar("bia").get("token").asText();
        String estudio = loginEstudio.get("token").asText();
        String estudioId = loginEstudio.get("id").asText();

        // Horário de funcionamento
        String disponibilidade = "/api/empresas/" + estudioId + "/disponibilidade";
        List<Map<String, String>> semana = List.of("SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO", "DOMINGO")
                .stream().map(d -> Map.of("diaSemana", d, "horaInicio", "08:00", "horaFim", "20:00")).toList();
        chamar("PUT", disponibilidade, Map.of("dias", semana), estudio, 200);
        get(disponibilidade, null);

        // Serviços
        String servicos = "/api/empresas/" + estudioId + "/servicos";
        String servicoId = get(servicos, estudio).get(0).get("id").asText();
        JsonNode novoServico = chamar("POST", servicos,
                Map.of("nome", "Escova", "duracaoMinutos", 30, "preco", 50), estudio, 201);
        String servicoExtra = novoServico.get("id").asText();
        chamar("PUT", servicos + "/" + servicoExtra,
                Map.of("nome", "Escova longa", "duracaoMinutos", 45, "preco", 60), estudio, 200);

        // Funcionárias, bloqueios e agenda
        String funcionarias = "/api/empresas/" + estudioId + "/funcionarias";
        String funcionariaId = chamar("POST", funcionarias,
                Map.of("nome", "Carla", "comissaoPercentual", 10), estudio, 201).get("id").asText();
        chamar("PUT", funcionarias + "/" + funcionariaId,
                Map.of("nome", "Carla Souza", "comissaoPercentual", 10), estudio, 200);
        get(funcionarias, estudio);

        LocalDate adiante = hoje.plusDays(3);
        String bloqueios = "/api/funcionarias/" + funcionariaId + "/bloqueios";
        String bloqueioId = chamar("POST", bloqueios, Map.of("dataHoraInicio", adiante.plusDays(2) + "T10:00:00",
                "dataHoraFim", adiante.plusDays(2) + "T11:00:00", "motivo", "Folga"), estudio, 201).get("id").asText();
        assertEquals(1, get(bloqueios, estudio).size());
        chamar("DELETE", bloqueios + "/" + bloqueioId, null, estudio, 204);

        // Vitrine
        get("/api/studios", null);
        get("/api/studios/pesquisa?texto=a&categoria=CABELO", null);
        get("/api/studios/busca?nome=studio", null);
        get("/api/studios/tipo?tipo=ESTABELECIMENTO", null);
        get("/api/studios/cidade?cidade=Goiania", null);
        get("/api/studios/" + estudioId, null);
        get("/api/studios/" + estudioId + "/funcionarias", null);
        get("/api/studios/" + estudioId + "/servicos", null);
        get(disponibilidade + "/slots?servicoId=" + servicoId + "&data=" + adiante, null);
        get(disponibilidade + "/slots?servicoId=" + servicoId + "&data=" + adiante + "&funcionariaId=" + funcionariaId, null);

        // Agendamento: criar, listar, remarcar dos dois lados e cancelar
        String remarcado = agendar(anaId, ana, estudioId, servicoId, funcionariaId, adiante + "T10:00:00");
        get("/api/agendamentos/cliente/" + anaId, ana);
        assertEquals("Corte", get("/api/agendamentos/empresa/" + estudioId, estudio).get(0).get("nomeServico").asText());
        get("/api/funcionarias/" + funcionariaId + "/agenda/dia?data=" + adiante, estudio);
        get("/api/funcionarias/" + funcionariaId + "/agenda/semana?dataInicio=" + adiante, estudio);
        chamar("PATCH", "/api/agendamentos/" + remarcado + "/reagendar/cliente",
                Map.of("novaDataHoraInicio", adiante + "T11:00:00"), ana, 200);
        chamar("PATCH", "/api/agendamentos/" + remarcado + "/reagendar/empresa",
                Map.of("novaDataHoraInicio", adiante + "T12:00:00"), estudio, 200);

        // Chat
        chamar("POST", "/api/mensagens/remetente/" + anaId,
                Map.of("agendamentoId", remarcado, "conteudo", "Oi"), ana, 201);
        JsonNode conversa = get("/api/mensagens/agendamento/" + remarcado, estudio);
        assertEquals("ana", conversa.get(0).get("nomeRemetente").asText());
        chamar("PATCH", "/api/mensagens/" + conversa.get(0).get("id").asText() + "/lida", null, estudio, 204);
        chamar("GET", "/api/mensagens/agendamento/" + remarcado, null, bia, 400);

        chamar("PATCH", "/api/agendamentos/" + remarcado + "/cancelar", null, ana, 200);
        String canceladoPeloEstudio = agendar(anaId, ana, estudioId, servicoId, null, adiante + "T15:00:00");
        chamar("PATCH", "/api/agendamentos/" + canceladoPeloEstudio + "/cancelar/empresa", null, estudio, 200);

        // Atendimento concluído: é o que enche as relações do painel, das comissões e das avaliações
        String concluido = agendar(anaId, ana, estudioId, servicoId, funcionariaId, adiante + "T16:00:00");
        LocalDate ontem = hoje.minusDays(1);
        jdbc.update("update agendamento set data_hora_inicio = ?::timestamp, data_hora_fim = ?::timestamp where id = ?::uuid",
                ontem + " 10:00:00", ontem + " 11:00:00", concluido);
        chamar("PATCH", "/api/agendamentos/" + concluido + "/concluir", null, estudio, 200);
        assertEquals(1, get("/api/agendamentos/cliente/" + anaId + "/historico", ana).size());

        assertEquals(1, get("/api/comissoes/funcionaria/" + funcionariaId, estudio).size());
        get("/api/comissoes/funcionaria/" + funcionariaId + "/total", estudio);

        chamar("POST", "/api/avaliacoes/cliente", Map.of("agendamentoId", concluido, "nota", 5, "comentario", "Ótimo"), ana, 201);
        chamar("POST", "/api/avaliacoes/empresa", Map.of("agendamentoId", concluido, "nota", 4), estudio, 201);
        get("/api/avaliacoes/cliente/" + anaId, ana);
        chamar("GET", "/api/avaliacoes/cliente/" + anaId, null, estudio, 400);
        assertEquals(5, get("/api/avaliacoes/minhas", ana).get(0).get("nota").asInt());
        assertEquals("ana", get("/api/avaliacoes/empresa/" + estudioId, null).get(0).get("nomeCliente").asText());
        get("/api/avaliacoes/empresa/" + estudioId + "/media", null);
        get("/api/studios/" + estudioId + "/avaliacoes", null);
        get("/api/studios/" + estudioId + "/media", null);

        // Painel: o caso que dava erro 500 só com banco de verdade
        JsonNode painel = get("/api/empresas/" + estudioId + "/dashboard?dataInicio=" + ontem.minusDays(1)
                + "&dataFim=" + hoje, estudio);
        assertEquals(1, painel.get("totalAtendimentos").asInt());
        assertEquals("Corte", painel.get("rankingServicos").get(0).get("nome").asText());
        assertEquals("ana", painel.get("rankingServicos").get(0).get("clientes").get(0).get("nome").asText());
        assertEquals("Carla Souza", painel.get("desempenhoPorFuncionaria").get(0).get("nome").asText());

        // Perfil
        JsonNode perfilAna = get("/api/clientes/" + anaId + "/perfil", ana);
        chamar("PUT", "/api/clientes/" + anaId + "/perfil", Map.of("nome", "Ana Lima", "telefone", "62988887777",
                "dataNascimento", perfilAna.get("dataNascimento").asText(), "genero", "FEMININO"), ana, 200);
        get("/api/empresas/" + estudioId + "/perfil", estudio);
        chamar("PUT", "/api/empresas/" + estudioId + "/perfil", Map.of("nome", "Studio Bella", "telefone", "62988886666",
                "tipoEstabelecimento", "AUTONOMA"), estudio, 200);

        // Lembrete: o job roda fora de uma requisição e lê as relações do agendamento
        String lembrado = agendar(anaId, ana, estudioId, servicoId, null, hoje.plusDays(1) + "T10:00:00");
        lembreteJob.enviarLembretes();
        ArgumentCaptor<SimpleMailMessage> enviados = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(email, atLeastOnce()).send(enviados.capture());
        assertTrue(enviados.getAllValues().stream().anyMatch(m -> m.getSubject().contains("Lembrete")
                && m.getText().contains("Corte") && m.getText().contains("Studio Bella")), lembrado);

        // Desativar e sessão
        chamar("DELETE", servicos + "/" + servicoExtra, null, estudio, 204);
        chamar("DELETE", funcionarias + "/" + funcionariaId, null, estudio, 204);
        String refresh = loginAna.get("refreshToken").asText();
        JsonNode renovado = chamar("POST", "/api/auth/refresh", Map.of("refreshToken", refresh), null, 200);
        chamar("POST", "/api/auth/logout", Map.of("refreshToken", renovado.get("refreshToken").asText()), null, 204);
        chamar("POST", "/api/auth/recuperar-senha", Map.of("email", "ana@example.com"), null, 200);

        // Id que não existe é 404, não erro interno
        chamar("GET", "/api/studios/" + UUID.randomUUID(), null, null, 404);
    }
}
