package br.com.kira.kirabackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Clientes HTTP dos serviços externos, um por serviço, cada um com os próprios timeouts.
 *
 * Ficam aqui para cada client (Cloudflare, Nominatim, kira-verify-ai) ter um construtor só, sem @Autowired,
 * e para os testes poderem passar um RestClient simulado no lugar destes.
 */
@Configuration
public class ClientesHttpConfig {

    public static final String CLOUDFLARE = "cloudflareRestClient";
    public static final String NOMINATIM = "nominatimRestClient";
    public static final String VERIFICACAO = "verificacaoRestClient";

    // O RestClient.Builder do Spring Boot é prototype: cada método recebe um builder novo, e um não afeta o outro
    @Bean(CLOUDFLARE)
    RestClient cloudflareRestClient(
            RestClient.Builder builder,
            @Value("${kira.provador.cloudflare.timeout-conexao-ms:5000}") long timeoutConexaoMs,
            @Value("${kira.provador.cloudflare.timeout-leitura-ms:90000}") long timeoutLeituraMs) {
        return comTimeouts(builder, timeoutConexaoMs, timeoutLeituraMs);
    }

    @Bean(NOMINATIM)
    RestClient nominatimRestClient(
            RestClient.Builder builder,
            @Value("${kira.geocodificacao.timeout-conexao-ms:2000}") long timeoutConexaoMs,
            @Value("${kira.geocodificacao.timeout-leitura-ms:3000}") long timeoutLeituraMs) {
        return comTimeouts(builder, timeoutConexaoMs, timeoutLeituraMs);
    }

    @Bean(VERIFICACAO)
    RestClient verificacaoRestClient(
            RestClient.Builder builder,
            @Value("${kira.verificacao.servico.timeout-conexao-ms:3000}") long timeoutConexaoMs,
            @Value("${kira.verificacao.servico.timeout-leitura-ms:30000}") long timeoutLeituraMs) {
        return comTimeouts(builder, timeoutConexaoMs, timeoutLeituraMs);
    }

    private static RestClient comTimeouts(RestClient.Builder builder, long timeoutConexaoMs, long timeoutLeituraMs) {
        ClientHttpRequestFactorySettings configuracao = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(timeoutConexaoMs))
                .withReadTimeout(Duration.ofMillis(timeoutLeituraMs));
        return builder.requestFactory(ClientHttpRequestFactories.get(configuracao)).build();
    }
}
