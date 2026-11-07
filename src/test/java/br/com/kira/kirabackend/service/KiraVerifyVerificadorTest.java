package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.enums.DecisaoVerificacaoIa;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.exception.FalhaVerificacaoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoIndisponivelException;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.DadosDoCadastro;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Imagem;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KiraVerifyVerificadorTest {

    private static final String BASE = "http://kira-verify-ai:8000";
    private static final String URL = BASE + "/verify/identity";
    private static final String CHAVE = "chave-de-teste";

    private static final Imagem DOCUMENTO = new Imagem(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, MediaType.IMAGE_JPEG);
    private static final Imagem SELFIE = new Imagem(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, MediaType.IMAGE_PNG);

    private MockRestServiceServer servidor;
    private RestClient restClient;
    private KiraVerifyVerificador verificador;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
        verificador = new KiraVerifyVerificador(restClient, BASE, CHAVE);
    }

    private static final DadosDoCadastro CADASTRO =
            new DadosDoCadastro("12345678900", "Maria da Silva", LocalDate.of(1990, 3, 12));

    // Formato do POST /verify/identity do kira-ai (docs/KIRA_VERIFY.md da Wanessa)
    private static String respostaJson(String status, String motivos) {
        return """
                {"status":"%s","reasons":%s,
                 "face":{"selfie_faces":1,"document_faces":1,"similarity":0.87,"decision":"match","issues":[]},
                 "document":{"extracted":{"cpf_masked":"***.456.789-**"},"checks":{"cpf":"match"}},
                 "liveness":"not_checked"}
                """.formatted(status, motivos);
    }

    private Resultado verificar() {
        return verificador.verificar(DOCUMENTO, null, SELFIE, TipoDocumentoIdentidade.CNH, CADASTRO);
    }

    @Test
    @DisplayName("Envia as duas imagens, os dados do cadastro e a chave no header")
    void enviaPedidoNoFormatoDoContrato() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Key", CHAVE))
                .andExpect(header("Content-Type", startsWith("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"document_image\"; filename=\"document_image.jpg\"")))
                .andExpect(content().string(containsString("name=\"selfie_image\"; filename=\"selfie_image.png\"")))
                .andExpect(content().string(containsString("Content-Type: image/png")))
                .andExpect(content().string(containsString("name=\"expected_cpf\"")))
                .andExpect(content().string(containsString("12345678900")))
                .andExpect(content().string(containsString("name=\"expected_name\"")))
                .andExpect(content().string(containsString("Maria da Silva")))
                .andExpect(content().string(containsString("name=\"expected_birth_date\"")))
                .andExpect(content().string(containsString("1990-03-12")))
                .andExpect(content().string(not(containsString("document_type"))))
                .andRespond(withSuccess(respostaJson("approved", "[]"), MediaType.APPLICATION_JSON));

        verificar();

        servidor.verify();
    }

    @Test
    @DisplayName("O verso não é enviado: o serviço trabalha só com a frente")
    void naoEnviaOVerso() {
        Imagem verso = new Imagem(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x02}, MediaType.IMAGE_JPEG);
        servidor.expect(requestTo(URL))
                .andExpect(content().string(not(containsString("document_back"))))
                .andRespond(withSuccess(respostaJson("approved", "[]"), MediaType.APPLICATION_JSON));

        verificador.verificar(DOCUMENTO, verso, SELFIE, TipoDocumentoIdentidade.RG, CADASTRO);

        servidor.verify();
    }

    @Test
    @DisplayName("Dado do cadastro que falta não é enviado")
    void semDadosDoCadastro() {
        servidor.expect(requestTo(URL))
                .andExpect(content().string(not(containsString("expected_"))))
                .andRespond(withSuccess(respostaJson("manual_review", "[]"), MediaType.APPLICATION_JSON));

        verificador.verificar(DOCUMENTO, null, SELFIE, TipoDocumentoIdentidade.RG,
                new DadosDoCadastro(" ", null, null));

        servidor.verify();
    }

    @ParameterizedTest(name = "Lê a decisão {0} da resposta como {1}")
    @CsvSource({"approved, APPROVED", "retry, RETRY", "manual_review, REVIEW", "rejected, REJECTED"})
    void leAsDecisoes(String status, DecisaoVerificacaoIa esperada) {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(respostaJson(status,
                        "[{\"code\":\"resolucao_baixa\",\"message\":\"Foto com resolução baixa.\"}]"),
                        MediaType.APPLICATION_JSON));

        Resultado resultado = verificar();

        assertEquals(esperada, resultado.decisao());
        assertEquals(List.of("resolucao_baixa"), resultado.motivos());
        assertEquals(new BigDecimal("0.87"), resultado.similaridade());
        // O serviço não devolve a confiança do OCR nem um id do pedido
        assertNull(resultado.confiancaOcr());
        assertNull(resultado.requestId());
    }

    @Test
    @DisplayName("Sem rosto utilizável, a similaridade vem nula")
    void semSimilaridade() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("""
                        {"status":"retry","reasons":[{"code":"selfie_sem_rosto","message":"Sem rosto."}],
                         "face":{"similarity":null,"decision":"unavailable"}}
                        """, MediaType.APPLICATION_JSON));

        Resultado resultado = verificar();

        assertEquals(DecisaoVerificacaoIa.RETRY, resultado.decisao());
        assertNull(resultado.similaridade());
    }

    @Test
    @DisplayName("Resposta sem reasons nem face vira lista vazia e similaridade nula")
    void semMotivos() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"status\":\"approved\"}", MediaType.APPLICATION_JSON));

        Resultado resultado = verificar();

        assertTrue(resultado.motivos().isEmpty());
        assertNull(resultado.similaridade());
    }

    @ParameterizedTest(name = "Resposta {0} vira falha de verificação")
    @ValueSource(strings = {"{\"status\":\"TALVEZ\"}", "{\"reasons\":[]}", "{\"status\":\"APPROVED\"}"})
    void respostaForaDoContrato(String corpo) {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

        assertThrows(FalhaVerificacaoException.class, this::verificar);
    }

    @Test
    @DisplayName("Sem a chave configurada, chama o serviço sem o header: o kira-ai não pede chave")
    void semChave() {
        KiraVerifyVerificador semChave = new KiraVerifyVerificador(restClient, BASE, " ");
        servidor.expect(requestTo(URL))
                .andExpect(headerDoesNotExist("X-Service-Key"))
                .andRespond(withSuccess(respostaJson("approved", "[]"), MediaType.APPLICATION_JSON));

        assertEquals(DecisaoVerificacaoIa.APPROVED,
                semChave.verificar(DOCUMENTO, null, SELFIE, TipoDocumentoIdentidade.CNH, CADASTRO).decisao());

        servidor.verify();
    }

    @Test
    @DisplayName("Chave recusada (401) vira falha de verificação, sem nova tentativa e sem a chave na mensagem")
    void chaveRecusada() {
        servidor.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        FalhaVerificacaoException erro = assertThrows(FalhaVerificacaoException.class, this::verificar);

        assertTrue(!erro.getMessage().contains(CHAVE));
        servidor.verify();
    }

    @ParameterizedTest(name = "Status {0} vira erro de regra de negócio (400 no app)")
    @ValueSource(ints = {413, 415})
    void imagemRecusada(int status) {
        servidor.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThrows(RegraDeNegocioException.class, this::verificar);
        servidor.verify();
    }

    @Test
    @DisplayName("Outro erro do serviço (500) vira falha de verificação, sem nova tentativa")
    void erroInterno() {
        servidor.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(FalhaVerificacaoException.class, this::verificar);
        servidor.verify();
    }

    @Test
    @DisplayName("503 seguido de sucesso: tenta de novo uma vez e devolve o resultado")
    void indisponivelUmaVez() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(respostaJson("manual_review", "[]"), MediaType.APPLICATION_JSON));

        assertEquals(DecisaoVerificacaoIa.REVIEW, verificar().decisao());
        servidor.verify();
    }

    @Test
    @DisplayName("503 duas vezes: desiste depois da segunda tentativa")
    void indisponivelDuasVezes() {
        servidor.expect(ExpectedCount.twice(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(VerificacaoIndisponivelException.class, this::verificar);
        servidor.verify();
    }

    @Test
    @DisplayName("Conexão recusada duas vezes: tenta de novo uma vez e avisa que está indisponível")
    void conexaoRecusada() {
        servidor.expect(ExpectedCount.twice(), requestTo(URL)).andRespond(withException(new ConnectException("recusada")));

        assertThrows(VerificacaoIndisponivelException.class, this::verificar);
        servidor.verify();
    }

    @Test
    @DisplayName("Timeout de leitura: não tenta de novo e avisa que está indisponível")
    void timeoutDeLeitura() {
        servidor.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(VerificacaoIndisponivelException.class, this::verificar);
        servidor.verify();
    }
}
