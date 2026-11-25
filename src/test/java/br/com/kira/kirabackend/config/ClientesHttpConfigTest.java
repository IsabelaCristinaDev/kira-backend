package br.com.kira.kirabackend.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Os tempos de leitura dos clientes HTTP, com um servidor local que demora para responder.
 * Confere que cada propriedade do application.yml chega ao seu cliente (um nome errado no @Value
 * passaria em silêncio, com o valor padrão) e que um cliente não herda o tempo do outro.
 * O tempo de conexão não é testado: precisaria de um endereço que não responde, o que fica instável no CI.
 */
@SpringBootTest(
        classes = ClientesHttpConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "kira.provador.compatibilidade.timeout-leitura-ms=" + ClientesHttpConfigTest.CURTO_MS,
                "kira.verificacao.servico.timeout-leitura-ms=" + ClientesHttpConfigTest.CURTO_MS,
                "kira.geocodificacao.timeout-leitura-ms=" + ClientesHttpConfigTest.CURTO_MS,
                "kira.provador.cloudflare.timeout-leitura-ms=5000"
        })
@ImportAutoConfiguration(RestClientAutoConfiguration.class)
class ClientesHttpConfigTest {

    static final int CURTO_MS = 300;
    private static final int DEMORA_DO_SERVIDOR_MS = 1000;

    private static HttpServer servidor;

    @Autowired
    private ApplicationContext contexto;

    @BeforeAll
    static void subirServidorLento() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.createContext("/lento", troca -> {
            try {
                Thread.sleep(DEMORA_DO_SERVIDOR_MS);
                byte[] corpo = "ok".getBytes(StandardCharsets.UTF_8);
                troca.sendResponseHeaders(200, corpo.length);
                troca.getResponseBody().write(corpo);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // O cliente desistiu antes e fechou a conexão: é o esperado nos testes de tempo esgotado
            } finally {
                troca.close();
            }
        });
        servidor.start();
    }

    @AfterAll
    static void derrubarServidor() {
        servidor.stop(0);
    }

    private String urlLenta() {
        return "http://" + servidor.getAddress().getHostString() + ":" + servidor.getAddress().getPort() + "/lento";
    }

    private RestClient cliente(String nome) {
        return contexto.getBean(nome, RestClient.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {ClientesHttpConfig.KIRA_AI, ClientesHttpConfig.VERIFICACAO, ClientesHttpConfig.NOMINATIM})
    @DisplayName("Desiste no tempo de leitura configurado, sem esperar a resposta lenta")
    void desisteNoTempoConfigurado(String nome) {
        RestClient cliente = cliente(nome);
        long antes = System.nanoTime();

        ResourceAccessException erro = assertThrows(ResourceAccessException.class,
                () -> cliente.get().uri(urlLenta()).retrieve().body(String.class));

        long esperouMs = (System.nanoTime() - antes) / 1_000_000;
        assertInstanceOf(SocketTimeoutException.class, erro.getCause());
        assertTrue(esperouMs >= CURTO_MS - 50, "desistiu antes do tempo: " + esperouMs + " ms");
        assertTrue(esperouMs < DEMORA_DO_SERVIDOR_MS - 200, "esperou a resposta lenta: " + esperouMs + " ms");
    }

    @Test
    @DisplayName("A Cloudflare usa o próprio tempo, maior, e recebe a resposta lenta")
    void cloudflareTemOProprioTempo() {
        String resposta = cliente(ClientesHttpConfig.CLOUDFLARE).get().uri(urlLenta()).retrieve().body(String.class);

        assertEquals("ok", resposta);
    }
}
