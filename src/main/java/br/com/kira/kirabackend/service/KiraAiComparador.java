package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.config.ClientesHttpConfig;
import br.com.kira.kirabackend.exception.CompatibilidadeIndisponivelException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Chama o kira-ai em POST /compatibility/rank: multipart com procedure, target_image e
 * portfolio_image_1 a 3. O serviço classifica cada foto e só devolve no ranking as de cabelo.
 *
 * Qualquer falha vira {@link CompatibilidadeIndisponivelException}, sem nova tentativa: o app tem a
 * ordem dele para usar no lugar, e esperar de novo só atrasaria a tela. Imagens e o corpo das
 * respostas de erro nunca vão para o log.
 */
@Service
public class KiraAiComparador implements ComparadorDePortfolio {

    private static final Logger log = LoggerFactory.getLogger(KiraAiComparador.class);

    private static final String CAMINHO = "/compatibility/rank";
    // O Provador hoje só trabalha com cabelo
    private static final String PROCEDIMENTO = "cabelo";

    private final RestClient restClient;
    private final String baseUrl;

    KiraAiComparador(
            @Qualifier(ClientesHttpConfig.KIRA_AI) RestClient restClient,
            @Value("${kira.provador.compatibilidade.base-url}") String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public List<Semelhanca> comparar(byte[] referencia, List<Foto> fotos) {
        if (fotos.size() > MAXIMO_DE_FOTOS) {
            throw new IllegalArgumentException("O kira-ai aceita no máximo " + MAXIMO_DE_FOTOS + " fotos por chamada");
        }
        if (fotos.isEmpty()) {
            return List.of();
        }

        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("procedure", PROCEDIMENTO);
        corpo.add("target_image", arquivo(referencia, "referencia"));
        for (int i = 0; i < fotos.size(); i++) {
            corpo.add("portfolio_image_" + (i + 1), arquivo(fotos.get(i).conteudo(), fotos.get(i).nome()));
        }

        Resposta resposta = enviar(corpo);
        if (resposta == null || resposta.ranking() == null) {
            log.error("Compatibilidade: resposta do kira-ai sem ranking");
            throw new CompatibilidadeIndisponivelException("O kira-ai devolveu uma resposta fora do contrato", null);
        }
        return resposta.ranking().stream()
                .map(item -> new Semelhanca(item.nome(), item.percentual()))
                .toList();
    }

    private Resposta enviar(MultiValueMap<String, Object> corpo) {
        try {
            return restClient.post()
                    .uri(baseUrl + CAMINHO)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(corpo)
                    .retrieve()
                    .body(Resposta.class);
        } catch (RestClientResponseException e) {
            log.error("Compatibilidade: kira-ai respondeu {}", e.getStatusCode().value());
            throw new CompatibilidadeIndisponivelException("O kira-ai recusou a solicitação", e);
        } catch (ResourceAccessException e) {
            // Serviço desligado ou demorando além do timeout
            log.warn("Compatibilidade: sem resposta do kira-ai ({})", nomeDaCausa(e));
            throw new CompatibilidadeIndisponivelException("O kira-ai não respondeu", e);
        } catch (RestClientException e) {
            log.error("Compatibilidade: resposta do kira-ai fora do contrato ({})", e.getClass().getSimpleName());
            throw new CompatibilidadeIndisponivelException("O kira-ai devolveu uma resposta fora do contrato", e);
        }
    }

    // O nome do arquivo precisa ir no multipart: é ele que o kira-ai devolve para identificar cada foto
    private static Resource arquivo(byte[] conteudo, String nome) {
        return new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        };
    }

    private static String nomeDaCausa(Exception e) {
        Throwable causa = e.getCause() != null ? e.getCause() : e;
        return causa.getClass().getSimpleName();
    }

    /** Só o que o backend usa da resposta. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(@JsonProperty("ranking") List<Item> ranking) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(
            @JsonProperty("portfolio_image") String nome,
            @JsonProperty("similarity_percentage") double percentual) {}
}
