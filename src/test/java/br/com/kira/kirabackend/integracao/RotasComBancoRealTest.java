package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.job.LembreteAgendamentoJob;
import br.com.kira.kirabackend.util.KiraTimeZone;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @TempDir
    private static Path pastaDeArquivos;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BANCO::getJdbcUrl);
        registro.add("spring.datasource.username", BANCO::getUsername);
        registro.add("spring.datasource.password", BANCO::getPassword);
        registro.add("jwt.secret", () -> "segredo-usado-so-neste-teste-".repeat(4));
        registro.add("jwt.refresh-expiration", () -> "604800000");
        registro.add("LEMBRETE_ANTECEDENCIA_HORAS", () -> "48");
        registro.add("kira.arquivos.pasta", () -> pastaDeArquivos.toString());
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

    private static final String FOTO_DE_PERFIL = "/api/perfil/foto";
    private static final String CONVERSAS = "/api/mensagens/conversas";
    private static final String DISPOSITIVOS = "/api/notificacoes/dispositivos/web";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0};
    // Um ponto válido da curva P-256 e 16 bytes, que é o formato exigido
    private static final String P256DH =
            "BGsX0fLhLEJH-Lzm5WOkQPJ3A32BLeszoPShOUXYmMKWT-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU";
    private static final String AUTH = "AAAAAAAAAAAAAAAAAAAAAA";

    private record Conta(String id, String token) {}

    // Criadas uma vez só: os testes abaixo usam as mesmas contas, sem depender da ordem
    private static Conta cliente;
    private static Conta outraCliente;
    private static Conta salao;
    private static Conta outroEstudio;
    private static String servicoDoSalao;

    @BeforeEach
    void prepararContas() throws IOException, InterruptedException {
        if (cliente != null) {
            return;
        }
        cadastrarCliente("carol", "39053344705");
        cadastrarCliente("duda", "12345678909");
        cadastrarEstudio("salao", "11444777000161");
        cadastrarEstudio("espaco", "12345678000195");
        outraCliente = conta("duda");
        salao = conta("salao");
        outroEstudio = conta("espaco");
        servicoDoSalao = get("/api/empresas/" + salao.id() + "/servicos", salao.token()).get(0).get("id").asText();
        List<Map<String, String>> semana = List.of("SEGUNDA", "TERCA", "QUARTA", "QUINTA", "SEXTA", "SABADO", "DOMINGO")
                .stream().map(d -> Map.of("diaSemana", d, "horaInicio", "08:00", "horaFim", "20:00")).toList();
        chamar("PUT", "/api/empresas/" + salao.id() + "/disponibilidade", Map.of("dias", semana), salao.token(), 200);
        cliente = conta("carol");
    }

    private void cadastrarEstudio(String nome, String cnpj) throws IOException, InterruptedException {
        chamar("POST", "/api/auth/registro/empresa", Map.of(
                "nome", nome, "email", nome + "@example.com", "senha", SENHA, "telefone", "62999990001",
                "cnpj", cnpj, "descricao", "Cortes", "endereco", endereco(),
                "tipoEstabelecimento", "ESTABELECIMENTO",
                "servicos", List.of(Map.of("nome", "Corte", "preco", "80", "duracao", "60"))), null, 201);
    }

    private Conta conta(String nome) throws IOException, InterruptedException {
        JsonNode login = entrar(nome);
        return new Conta(login.get("id").asText(), login.get("token").asText());
    }

    private HttpResponse<String> enviarArquivo(String caminho, String campo, byte[] conteudo, String token)
            throws IOException, InterruptedException {
        String limite = "limite-do-teste";
        String inicio = "--" + limite + "\r\nContent-Disposition: form-data; name=\"" + campo
                + "\"; filename=\"foto.png\"\r\nContent-Type: image/png\r\n\r\n";
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        corpo.write(inicio.getBytes(StandardCharsets.UTF_8));
        corpo.write(conteudo);
        corpo.write(("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo.toByteArray()));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<Void> abrir(String caminho) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.discarding());
    }

    private long arquivosNaPasta() throws IOException {
        try (var arquivos = Files.list(pastaDeArquivos)) {
            return arquivos.count();
        }
    }

    private String agendamentoNoPassado(int diasAdiante, int hora) throws IOException, InterruptedException {
        LocalDate dia = hoje.plusDays(diasAdiante);
        String horario = "T%02d:00:00".formatted(hora);
        String id = agendar(cliente.id(), cliente.token(), salao.id(), servicoDoSalao, null, dia + horario);
        LocalDate ontem = hoje.minusDays(1);
        jdbc.update("update agendamento set data_hora_inicio = ?::timestamp, data_hora_fim = ?::timestamp where id = ?::uuid",
                ontem + " %02d:00:00".formatted(hora), ontem + " %02d:00:00".formatted(hora + 1), id);
        return id;
    }

    private JsonNode conversa(String agendamentoId, String token) throws IOException, InterruptedException {
        for (JsonNode linha : get(CONVERSAS, token)) {
            if (agendamentoId.equals(linha.get("agendamentoId").asText())) {
                return linha;
            }
        }
        return null;
    }

    private int dispositivosComEndereco(String endereco) {
        return jdbc.queryForObject("select count(*) from dispositivo_notificacao where endpoint = ?",
                Integer.class, endereco);
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

    @Test
    @DisplayName("Ao concluir, a forma de pagamento vem do corpo do PATCH ou fica a do agendamento")
    void formaDePagamentoAoConcluir() throws IOException, InterruptedException {
        String comForma = agendamentoNoPassado(5, 10);
        String semCorpo = agendamentoNoPassado(5, 12);
        String recusado = agendamentoNoPassado(5, 14);
        String concluir = "/api/agendamentos/%s/concluir";

        JsonNode concluido = chamar("PATCH", concluir.formatted(comForma), Map.of("formaPagamento", "PIX"),
                salao.token(), 200);
        assertEquals("PIX", concluido.get("formaPagamento").asText());
        assertEquals("CONCLUIDO", concluido.get("status").asText());

        JsonNode mantido = chamar("PATCH", concluir.formatted(semCorpo), null, salao.token(), 200);
        assertEquals("DINHEIRO", mantido.get("formaPagamento").asText());

        // Valor que não existe e estúdio errado não concluem: a chamada certa depois disso ainda funciona
        chamar("PATCH", concluir.formatted(recusado), Map.of("formaPagamento", "CHEQUE"), salao.token(), 400);
        chamar("PATCH", concluir.formatted(recusado), Map.of("formaPagamento", "PIX"), outroEstudio.token(), 400);
        chamar("PATCH", concluir.formatted(recusado), Map.of("formaPagamento", "PIX"), cliente.token(), 400);
        assertEquals("CARTAO_DEBITO", chamar("PATCH", concluir.formatted(recusado),
                Map.of("formaPagamento", "CARTAO_DEBITO"), salao.token(), 200).get("formaPagamento").asText());
    }

    @Test
    @DisplayName("Foto de perfil: envia, abre sem login, troca, recusa o que não é imagem e remove")
    void fotoDePerfil() throws IOException, InterruptedException {
        String token = outraCliente.token();
        assertEquals(401, enviarArquivo(FOTO_DE_PERFIL, "arquivo", PNG, null).statusCode());

        HttpResponse<String> enviada = enviarArquivo(FOTO_DE_PERFIL, "arquivo", PNG, token);
        assertEquals(200, enviada.statusCode(), enviada.body());
        String primeira = json.readTree(enviada.body()).get("fotoUrl").asText();
        assertEquals(primeira, get("/api/clientes/" + outraCliente.id() + "/perfil", token).get("fotoUrl").asText());

        HttpResponse<Void> aberta = abrir(primeira);
        assertEquals(200, aberta.statusCode());
        assertEquals("image/png", aberta.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("nosniff", aberta.headers().firstValue("X-Content-Type-Options").orElseThrow());

        String segunda = json.readTree(enviarArquivo(FOTO_DE_PERFIL, "arquivo", JPEG, token).body()).get("fotoUrl").asText();
        assertNotEquals(primeira, segunda);
        assertEquals(404, abrir(primeira).statusCode());
        assertEquals(200, abrir(segunda).statusCode());

        // Recusados: nada sobra na pasta
        long antes = arquivosNaPasta();
        byte[] grande = Arrays.copyOf(PNG, 5 * 1024 * 1024 + 1);
        byte[] script = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertEquals(400, enviarArquivo(FOTO_DE_PERFIL, "arquivo", script, token).statusCode());
        assertEquals(400, enviarArquivo(FOTO_DE_PERFIL, "arquivo", new byte[0], token).statusCode());
        assertEquals(400, enviarArquivo(FOTO_DE_PERFIL, "outro", PNG, token).statusCode());
        HttpResponse<String> acimaDoLimite = enviarArquivo(FOTO_DE_PERFIL, "arquivo", grande, token);
        assertEquals(413, acimaDoLimite.statusCode());
        assertEquals("ARQUIVO_GRANDE", json.readTree(acimaDoLimite.body()).get("erro").asText());
        assertEquals(antes, arquivosNaPasta());

        // Só abre o que foi guardado pelo sistema
        assertEquals(404, abrir("/api/arquivos/qualquer").statusCode());
        assertEquals(404, abrir("/api/arquivos/abc.png").statusCode());
        assertEquals(404, abrir("/api/arquivos/" + UUID.randomUUID() + ".html").statusCode());

        chamar("DELETE", FOTO_DE_PERFIL, null, null, 401);
        chamar("DELETE", FOTO_DE_PERFIL, null, token, 204);
        assertEquals(404, abrir(segunda).statusCode());
        assertTrue(get("/api/clientes/" + outraCliente.id() + "/perfil", token).get("fotoUrl").isNull());
        chamar("DELETE", FOTO_DE_PERFIL, null, token, 204);
    }

    @Test
    @DisplayName("Portfólio: público para ver, só o dono muda, e aceita até 20 fotos")
    void portfolio() throws IOException, InterruptedException {
        String publico = "/api/studios/" + outroEstudio.id() + "/portfolio";
        String doDono = "/api/empresas/" + outroEstudio.id() + "/portfolio";
        assertEquals(0, get(publico, null).size());

        assertEquals(401, enviarArquivo(doDono, "arquivo", PNG, null).statusCode());
        assertEquals(400, enviarArquivo(doDono, "arquivo", PNG, salao.token()).statusCode());
        assertEquals(400, enviarArquivo(doDono, "arquivo", PNG, cliente.token()).statusCode());

        HttpResponse<String> enviada = enviarArquivo(doDono, "arquivo", PNG, outroEstudio.token());
        assertEquals(201, enviada.statusCode(), enviada.body());
        JsonNode foto = json.readTree(enviada.body());
        String fotoId = foto.get("id").asText();
        String url = foto.get("url").asText();
        assertEquals(200, abrir(url).statusCode());
        assertEquals(url, get(publico, null).get(0).get("url").asText());

        chamar("DELETE", doDono + "/" + fotoId, null, salao.token(), 400);
        chamar("DELETE", "/api/empresas/" + salao.id() + "/portfolio/" + fotoId, null, salao.token(), 404);
        assertEquals(200, abrir(url).statusCode());

        chamar("GET", "/api/studios/" + UUID.randomUUID() + "/portfolio", null, null, 404);
        chamar("GET", "/api/studios/" + cliente.id() + "/portfolio", null, null, 404);

        for (int i = 2; i <= 20; i++) {
            assertEquals(201, enviarArquivo(doDono, "arquivo", PNG, outroEstudio.token()).statusCode());
        }
        assertEquals(20, get(publico, null).size());
        assertEquals(400, enviarArquivo(doDono, "arquivo", PNG, outroEstudio.token()).statusCode());
        assertEquals(20, get(publico, null).size());

        chamar("DELETE", doDono + "/" + fotoId, null, outroEstudio.token(), 204);
        assertEquals(404, abrir(url).statusCode());
        assertEquals(19, get(publico, null).size());
    }

    @Test
    @DisplayName("Lista de conversas e marcar todas como lidas respeitam quem participa")
    void listaDeConversas() throws IOException, InterruptedException {
        String agendamento = agendar(cliente.id(), cliente.token(), salao.id(), servicoDoSalao, null,
                hoje.plusDays(6) + "T10:00:00");
        String enviar = "/api/mensagens/remetente/" + cliente.id();
        chamar("POST", enviar, Map.of("agendamentoId", agendamento, "conteudo", "Oi"), cliente.token(), 201);
        chamar("POST", enviar, Map.of("agendamentoId", agendamento, "conteudo", "Tudo bem?"), cliente.token(), 201);

        JsonNode doEstudio = conversa(agendamento, salao.token());
        assertEquals(2, doEstudio.get("naoLidas").asInt());
        assertEquals("Tudo bem?", doEstudio.get("ultimaMensagem").asText());
        assertFalse(doEstudio.get("ultimaMensagemMinha").asBoolean());
        assertEquals("carol", doEstudio.get("nomeOutraParte").asText());
        assertEquals("Corte", doEstudio.get("nomeServico").asText());

        JsonNode daCliente = conversa(agendamento, cliente.token());
        assertEquals(0, daCliente.get("naoLidas").asInt());
        assertTrue(daCliente.get("ultimaMensagemMinha").asBoolean());
        assertEquals("salao", daCliente.get("nomeOutraParte").asText());

        assertNull(conversa(agendamento, outraCliente.token()));
        chamar("GET", CONVERSAS, null, null, 401);

        String lidas = "/api/mensagens/agendamento/" + agendamento + "/lidas";
        chamar("PATCH", lidas, null, null, 401);
        chamar("PATCH", lidas, null, outraCliente.token(), 400);
        assertEquals(2, conversa(agendamento, salao.token()).get("naoLidas").asInt());

        chamar("PATCH", lidas, null, salao.token(), 204);
        assertEquals(0, conversa(agendamento, salao.token()).get("naoLidas").asInt());
    }

    @Test
    @DisplayName("Notificações: chave pública e registro do navegador, cada conta só com o seu")
    void rotasDeNotificacao() throws IOException, InterruptedException {
        chamar("GET", "/api/notificacoes/chave-publica", null, null, 401);
        assertTrue(get("/api/notificacoes/chave-publica", cliente.token()).has("chavePublica"));

        String endereco = "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
        Map<String, String> navegador = Map.of("endpoint", endereco, "p256dh", P256DH, "auth", AUTH);
        chamar("POST", DISPOSITIVOS, navegador, null, 401);
        chamar("POST", DISPOSITIVOS, Map.of("endpoint", "https://exemplo.test/push", "p256dh", P256DH, "auth", AUTH),
                cliente.token(), 400);
        chamar("POST", DISPOSITIVOS, Map.of("endpoint", endereco, "p256dh", "AAAA", "auth", AUTH), cliente.token(), 400);
        assertEquals(0, dispositivosComEndereco(endereco));

        chamar("POST", DISPOSITIVOS, navegador, cliente.token(), 204);
        chamar("POST", DISPOSITIVOS, navegador, cliente.token(), 204);
        assertEquals(1, dispositivosComEndereco(endereco));

        // Outra conta não remove o navegador de quem registrou
        chamar("DELETE", DISPOSITIVOS, Map.of("endpoint", endereco), outraCliente.token(), 204);
        assertEquals(1, dispositivosComEndereco(endereco));

        chamar("DELETE", DISPOSITIVOS, Map.of("endpoint", endereco), cliente.token(), 204);
        assertEquals(0, dispositivosComEndereco(endereco));
    }
}
