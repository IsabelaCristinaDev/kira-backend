package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.enums.DecisaoVerificacaoIa;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.exception.FalhaVerificacaoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoIndisponivelException;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Imagem;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KiraVerifyVerificadorTest {

    private static final String BASE = "http://kira-verify-ai:8000";
    private static final String URL = BASE + "/v1/verify";
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

    private static String respostaJson(String status, String motivos) {
        return """
                {"status":"%s","reasons":%s,"similarity":0.87,"ocr_confidence":0.93,
                 "request_id":"req-123","ocr":{"cpf":"00000000000","nome":"Fulana"}}
                """.formatted(status, motivos);
    }

    private Resultado verificar() {
        return verificador.verificar(DOCUMENTO, null, SELFIE, TipoDocumentoIdentidade.CNH, "12345678900");
    }

    @Test
    @DisplayName("Envia as duas imagens, o tipo do documento, o CPF esperado e a chave no header")
    void enviaPedidoNoFormatoDoContrato() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Key", CHAVE))
                .andExpect(header("Content-Type", startsWith("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"document\"; filename=\"document.jpg\"")))
                .andExpect(content().string(containsString("name=\"selfie\"; filename=\"selfie.png\"")))
                .andExpect(content().string(containsString("Content-Type: image/png")))
                .andExpect(content().string(containsString("name=\"document_type\"")))
                .andExpect(content().string(containsString("CNH")))
                .andExpect(content().string(containsString("name=\"expected_cpf\"")))
                .andRespond(withSuccess(respostaJson("APPROVED", "[]"), MediaType.APPLICATION_JSON));

        verificar();

        servidor.verify();
    }

    @Test
    @DisplayName("Com o verso, envia também a parte document_back")
    void enviaOVerso() {
        Imagem verso = new Imagem(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x02}, MediaType.IMAGE_JPEG);
        servidor.expect(requestTo(URL))
                .andExpect(content().string(containsString("name=\"document\"; filename=\"document.jpg\"")))
                .andExpect(content().string(containsString("name=\"document_back\"; filename=\"document_back.jpg\"")))
                .andRespond(withSuccess(respostaJson("APPROVED", "[]"), MediaType.APPLICATION_JSON));

        verificador.verificar(DOCUMENTO, verso, SELFIE, TipoDocumentoIdentidade.RG, null);

        servidor.verify();
    }

    @Test
    @DisplayName("Sem o verso, a parte document_back não é enviada")
    void semVerso() {
        servidor.expect(requestTo(URL))
                .andExpect(content().string(not(containsString("document_back"))))
                .andRespond(withSuccess(respostaJson("APPROVED", "[]"), MediaType.APPLICATION_JSON));

        verificar();

        servidor.verify();
    }

    @Test
    @DisplayName("Sem CPF esperado, o campo expected_cpf não é enviado")
    void semCpfEsperado() {
        servidor.expect(requestTo(URL))
                .andExpect(content().string(not(containsString("expected_cpf"))))
                .andRespond(withSuccess(respostaJson("APPROVED", "[]"), MediaType.APPLICATION_JSON));

        verificador.verificar(DOCUMENTO, null, SELFIE, TipoDocumentoIdentidade.RG, null);

        servidor.verify();
    }

    @ParameterizedTest(name = "Lê a decisão {0} da resposta")
    @EnumSource(DecisaoVerificacaoIa.class)
    void leAsTresDecisoes(DecisaoVerificacaoIa decisao) {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(respostaJson(decisao.name(), "[\"LOW_IMAGE_QUALITY\"]"),
                        MediaType.APPLICATION_JSON));

        Resultado resultado = verificar();

        assertEquals(decisao, resultado.decisao());
        assertEquals(List.of("LOW_IMAGE_QUALITY"), resultado.motivos());
        assertEquals(new BigDecimal("0.87"), resultado.similaridade());
        assertEquals(new BigDecimal("0.93"), resultado.confiancaOcr());
        assertEquals("req-123", resultado.requestId());
    }

    @Test
    @DisplayName("Resposta sem reasons vira lista vazia")
    void semMotivos() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess("{\"status\":\"APPROVED\",\"request_id\":\"r\"}", MediaType.APPLICATION_JSON));

        assertTrue(verificar().motivos().isEmpty());
    }

    @ParameterizedTest(name = "Resposta {0} vira falha de verificação")
    @ValueSource(strings = {"{\"status\":\"TALVEZ\"}", "{\"reasons\":[]}"})
    void respostaForaDoContrato(String corpo) {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

        assertThrows(FalhaVerificacaoException.class, this::verificar);
    }

    @Test
    @DisplayName("Sem a chave configurada, nem chama o serviço")
    void semChave() {
        KiraVerifyVerificador semChave = new KiraVerifyVerificador(restClient, BASE, " ");

        assertThrows(FalhaVerificacaoException.class,
                () -> semChave.verificar(DOCUMENTO, null, SELFIE, TipoDocumentoIdentidade.CNH, null));

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
                .andRespond(withSuccess(respostaJson("REVIEW", "[]"), MediaType.APPLICATION_JSON));

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
