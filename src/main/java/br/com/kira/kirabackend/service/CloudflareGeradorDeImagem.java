package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.exception.GeracaoProvadorException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Gera a prévia do Provador pela API REST da Cloudflare (Workers AI), no mesmo formato do
 * script de teste tryon-cloudflare.mjs: multipart com o prompt e as duas imagens, resposta
 * JSON com a imagem em base64 em result.image.
 *
 * Fotos, token e imagem gerada nunca vão para o log.
 */
@Service
public class CloudflareGeradorDeImagem implements GeradorDeImagem {

    private static final Logger log = LoggerFactory.getLogger(CloudflareGeradorDeImagem.class);

    private static final String PROMPT_RECURSO = "provador/prompt-tryon.txt";
    private static final int TAMANHO_MAXIMO_LOG = 300;

    private final RestClient restClient;
    private final String accountId;
    private final String apiToken;
    private final String modelo;
    private final String baseUrl;
    private final String prompt;

    @Autowired
    public CloudflareGeradorDeImagem(
            RestClient.Builder builder,
            @Value("${kira.provador.cloudflare.account-id:}") String accountId,
            @Value("${kira.provador.cloudflare.api-token:}") String apiToken,
            @Value("${kira.provador.cloudflare.modelo}") String modelo,
            @Value("${kira.provador.cloudflare.base-url}") String baseUrl,
            @Value("${kira.provador.cloudflare.timeout-conexao-ms:5000}") long timeoutConexaoMs,
            @Value("${kira.provador.cloudflare.timeout-leitura-ms:90000}") long timeoutLeituraMs) {
        this(criarRestClient(builder, timeoutConexaoMs, timeoutLeituraMs), accountId, apiToken, modelo, baseUrl);
    }

    CloudflareGeradorDeImagem(RestClient restClient, String accountId, String apiToken, String modelo, String baseUrl) {
        this.restClient = restClient;
        this.accountId = accountId;
        this.apiToken = apiToken;
        this.modelo = modelo;
        this.baseUrl = baseUrl;
        this.prompt = lerPrompt();
    }

    @Override
    public byte[] gerar(byte[] fotoCliente, byte[] fotoReferencia) {
        if (accountId.isBlank() || apiToken.isBlank()) {
            log.error("Provador: CLOUDFLARE_ACCOUNT_ID e CLOUDFLARE_API_TOKEN não configurados");
            throw new GeracaoProvadorException("Credenciais do gerador de imagem não configuradas");
        }
        if (!accountId.matches("[A-Za-z0-9]+")) {
            log.error("Provador: CLOUDFLARE_ACCOUNT_ID com formato inválido");
            throw new GeracaoProvadorException("Identificador da conta do gerador de imagem inválido");
        }

        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("prompt", prompt);
        corpo.add("input_image_0", parteDeImagem(fotoCliente, "cliente.jpg"));
        corpo.add("input_image_1", parteDeImagem(fotoReferencia, "referencia.jpg"));

        JsonNode resposta = enviar(corpo);
        return extrairImagem(resposta);
    }

    private JsonNode enviar(MultiValueMap<String, Object> corpo) {
        String url = baseUrl + "/accounts/" + accountId + "/ai/run/" + modelo;
        try {
            return restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(corpo)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("Provador: o gerador respondeu {} ({})", e.getStatusCode().value(),
                    resumo(e.getResponseBodyAsString()));
            throw new GeracaoProvadorException("O gerador de imagem recusou a solicitação", e);
        } catch (RestClientException e) {
            log.error("Provador: falha de comunicação com o gerador: {}", e.getClass().getSimpleName());
            throw new GeracaoProvadorException("Não foi possível falar com o gerador de imagem", e);
        }
    }

    private byte[] extrairImagem(JsonNode resposta) {
        if (resposta == null || !resposta.path("success").asBoolean(false)) {
            log.error("Provador: o gerador devolveu erro ({})",
                    resposta == null ? "sem corpo" : resumo(resposta.path("errors").toString()));
            throw new GeracaoProvadorException("O gerador de imagem devolveu erro");
        }
        String imagemBase64 = resposta.path("result").path("image").asText("");
        if (imagemBase64.isBlank()) {
            log.error("Provador: a resposta do gerador veio sem result.image");
            throw new GeracaoProvadorException("O gerador de imagem não devolveu a imagem");
        }
        try {
            return Base64.getDecoder().decode(imagemBase64);
        } catch (IllegalArgumentException e) {
            log.error("Provador: result.image não é base64 válido");
            throw new GeracaoProvadorException("A imagem devolvida pelo gerador é inválida", e);
        }
    }

    // Mesmo formato do script de teste: o arquivo vai como octet-stream, com nome
    private static HttpEntity<Resource> parteDeImagem(byte[] bytes, String nome) {
        Resource arquivo = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return nome;
            }
        };
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        return new HttpEntity<>(arquivo, cabecalhos);
    }

    private static RestClient criarRestClient(RestClient.Builder builder, long timeoutConexaoMs, long timeoutLeituraMs) {
        ClientHttpRequestFactorySettings configuracao = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(timeoutConexaoMs))
                .withReadTimeout(Duration.ofMillis(timeoutLeituraMs));
        return builder.requestFactory(ClientHttpRequestFactories.get(configuracao)).build();
    }

    private static String lerPrompt() {
        try {
            return StreamUtils.copyToString(new ClassPathResource(PROMPT_RECURSO).getInputStream(), StandardCharsets.UTF_8)
                    .trim();
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt do Provador não encontrado: " + PROMPT_RECURSO, e);
        }
    }

    private static String resumo(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.length() <= TAMANHO_MAXIMO_LOG ? texto : texto.substring(0, TAMANHO_MAXIMO_LOG) + "...";
    }
}
