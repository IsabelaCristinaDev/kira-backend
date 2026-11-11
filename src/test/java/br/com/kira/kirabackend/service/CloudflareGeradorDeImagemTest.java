package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.exception.GeracaoProvadorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CloudflareGeradorDeImagemTest {

    private static final String BASE = "https://api.cloudflare.com/client/v4";
    private static final String MODELO = "@cf/black-forest-labs/flux-2-klein-4b";
    private static final String URL = BASE + "/accounts/conta123/ai/run/" + MODELO;

    private static final byte[] FOTO = {1, 2, 3};

    private MockRestServiceServer servidor;
    private CloudflareGeradorDeImagem gerador;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        gerador = new CloudflareGeradorDeImagem(builder.build(), "conta123", "token-secreto", MODELO, BASE);
    }

    @Test
    @DisplayName("Envia prompt e as duas fotos com o token e devolve a imagem decodificada")
    void sucesso() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        String json = "{\"success\":true,\"errors\":[],\"result\":{\"image\":\""
                + Base64.getEncoder().encodeToString(png) + "\"}}";
        servidor.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-secreto"))
                .andExpect(header("Content-Type", startsWith("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"prompt\"")))
                .andExpect(content().string(containsString("name=\"input_image_0\"")))
                .andExpect(content().string(containsString("name=\"input_image_1\"")))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        assertArrayEquals(png, gerador.gerar(FOTO, FOTO));

        servidor.verify();
    }

    static Stream<Named<String>> respostasSemImagemValida() {
        return Stream.of(
                Named.of("Resposta com success falso",
                        "{\"success\":false,\"errors\":[{\"code\":1000,\"message\":\"x\"}],\"result\":null}"),
                Named.of("Resposta sem result.image",
                        "{\"success\":true,\"result\":{}}"),
                Named.of("Imagem que não é base64 válido",
                        "{\"success\":true,\"result\":{\"image\":\"%%%nao-e-base64%%%\"}}"));
    }

    @ParameterizedTest(name = "{0} vira erro de geração")
    @MethodSource("respostasSemImagemValida")
    void respostaSemImagemValida(String corpo) {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));

        assertThrows(GeracaoProvadorException.class, () -> gerador.gerar(FOTO, FOTO));
    }

    @Test
    @DisplayName("Erro HTTP do serviço vira erro de geração")
    void erroHttp() {
        servidor.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(GeracaoProvadorException.class, () -> gerador.gerar(FOTO, FOTO));
    }

    @Test
    @DisplayName("Sem credenciais configuradas, não chama a Cloudflare")
    void semCredenciais() {
        CloudflareGeradorDeImagem semToken =
                new CloudflareGeradorDeImagem(RestClient.builder().build(), "conta123", "", MODELO, BASE);

        assertThrows(GeracaoProvadorException.class, () -> semToken.gerar(FOTO, FOTO));
    }

    @Test
    @DisplayName("Identificador de conta com caracteres estranhos é recusado antes de montar o endereço")
    void contaInvalida() {
        CloudflareGeradorDeImagem invalido =
                new CloudflareGeradorDeImagem(RestClient.builder().build(), "../outra", "token", MODELO, BASE);

        assertThrows(GeracaoProvadorException.class, () -> invalido.gerar(FOTO, FOTO));
    }
}
