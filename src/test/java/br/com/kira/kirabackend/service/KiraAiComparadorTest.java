package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.exception.CompatibilidadeIndisponivelException;
import br.com.kira.kirabackend.service.ComparadorDePortfolio.Foto;
import br.com.kira.kirabackend.service.ComparadorDePortfolio.Semelhanca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KiraAiComparadorTest {

    private static final String BASE = "http://kira-ai:8000";
    private static final String URL = BASE + "/compatibility/rank";

    private static final byte[] REFERENCIA = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
    private static final Foto FOTO_1 = new Foto("foto-1.jpg", new byte[]{1});
    // Fora do lambda do assertThrows: lá dentro só fica a chamada testada
    private static final List<Foto> SO_FOTO_1 = List.of(FOTO_1);
    private static final Foto FOTO_2 = new Foto("foto-2.png", new byte[]{2});

    private MockRestServiceServer servidor;
    private KiraAiComparador comparador;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        comparador = new KiraAiComparador(builder.build(), BASE);
    }

    private static String resposta(String ranking) {
        return """
                {"requested_procedure":"cabelo","total_compared":1,"total_rejected":1,
                 "ranking":%s,
                 "rejected_images":[{"portfolio_image":"foto-2.png","detected_category":"unhas",
                                     "reason":"Categoria diferente do procedimento solicitado"}]}
                """.formatted(ranking);
    }

    @Test
    @DisplayName("Envia o procedimento cabelo, a referência e cada foto com o próprio nome")
    void enviaPedidoNoFormatoDoContrato() {
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", startsWith("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"procedure\"")))
                .andExpect(content().string(containsString("cabelo")))
                .andExpect(content().string(containsString("name=\"target_image\"; filename=\"referencia\"")))
                .andExpect(content().string(containsString("name=\"portfolio_image_1\"; filename=\"foto-1.jpg\"")))
                .andExpect(content().string(containsString("name=\"portfolio_image_2\"; filename=\"foto-2.png\"")))
                .andExpect(content().string(not(containsString("portfolio_image_3"))))
                .andRespond(withSuccess(resposta("[]"), MediaType.APPLICATION_JSON));

        comparador.comparar(REFERENCIA, List.of(FOTO_1, FOTO_2));

        servidor.verify();
    }

    @Test
    @DisplayName("Devolve só as fotos aceitas, com o percentual de semelhança")
    void devolveSoAsFotosAceitas() {
        servidor.expect(requestTo(URL))
                .andRespond(withSuccess(resposta("""
                        [{"portfolio_image":"foto-1.jpg","category":"cabelo","similarity":0.7796,
                          "similarity_percentage":77.96}]
                        """), MediaType.APPLICATION_JSON));

        List<Semelhanca> resultado = comparador.comparar(REFERENCIA, List.of(FOTO_1, FOTO_2));

        assertEquals(List.of(new Semelhanca("foto-1.jpg", 77.96)), resultado);
    }

    @Test
    @DisplayName("Sem nenhuma foto aceita, devolve lista vazia")
    void nenhumaFotoAceita() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(resposta("[]"), MediaType.APPLICATION_JSON));

        assertTrue(comparador.comparar(REFERENCIA, List.of(FOTO_1)).isEmpty());
    }

    @Test
    @DisplayName("Sem fotos, nem chama o serviço")
    void semFotosNaoChama() {
        assertTrue(comparador.comparar(REFERENCIA, List.of()).isEmpty());

        servidor.verify();
    }

    @Test
    @DisplayName("Mais de 3 fotos é erro de programação: o serviço só aceita 3 por vez")
    void maisDeTresFotos() {
        List<Foto> quatro = List.of(FOTO_1, FOTO_1, FOTO_1, FOTO_1);

        assertThrows(IllegalArgumentException.class, () -> comparador.comparar(REFERENCIA, quatro));
    }

    @Test
    @DisplayName("Serviço desligado (conexão recusada) vira indisponível")
    void servicoDesligado() {
        servidor.expect(requestTo(URL)).andRespond(withException(new ConnectException("Connection refused")));

        assertThrows(CompatibilidadeIndisponivelException.class,
                () -> comparador.comparar(REFERENCIA, SO_FOTO_1));
    }

    @Test
    @DisplayName("Serviço que demora demais vira indisponível")
    void timeout() {
        servidor.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(CompatibilidadeIndisponivelException.class,
                () -> comparador.comparar(REFERENCIA, SO_FOTO_1));
    }

    @ParameterizedTest(name = "HTTP {0}")
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNPROCESSABLE_ENTITY", "INTERNAL_SERVER_ERROR",
            "SERVICE_UNAVAILABLE"})
    @DisplayName("Resposta de erro do serviço vira indisponível, sem repassar o corpo do erro")
    void respostaDeErro(HttpStatus status) {
        servidor.expect(requestTo(URL)).andRespond(withStatus(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"Uma das imagens enviadas é inválida.\"}"));

        CompatibilidadeIndisponivelException erro = assertThrows(CompatibilidadeIndisponivelException.class,
                () -> comparador.comparar(REFERENCIA, SO_FOTO_1));

        assertFalse(erro.getMessage().contains("inválida"));
    }

    @Test
    @DisplayName("Resposta que não segue o contrato vira indisponível")
    void respostaForaDoContrato() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("isto não é json", MediaType.APPLICATION_JSON));

        assertThrows(CompatibilidadeIndisponivelException.class,
                () -> comparador.comparar(REFERENCIA, SO_FOTO_1));
    }

    @Test
    @DisplayName("Resposta sem a lista de ranking vira indisponível")
    void respostaSemRanking() {
        servidor.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(CompatibilidadeIndisponivelException.class,
                () -> comparador.comparar(REFERENCIA, SO_FOTO_1));
    }

    @Test
    @DisplayName("O toString da foto não mostra os bytes da imagem")
    void fotoSemBytesNoLog() {
        assertEquals("Foto[foto-1.jpg, 1 bytes]", FOTO_1.toString());
    }

    @Test
    @DisplayName("Duas fotos com o mesmo nome e o mesmo conteúdo são iguais")
    void fotoIgualPeloConteudo() {
        Foto copia = new Foto("foto-1.jpg", new byte[]{1});
        Foto outra = new Foto("foto-1.jpg", new byte[]{2});

        assertEquals(FOTO_1, copia);
        assertEquals(FOTO_1.hashCode(), copia.hashCode());
        assertNotEquals(FOTO_1, outra);
    }
}
