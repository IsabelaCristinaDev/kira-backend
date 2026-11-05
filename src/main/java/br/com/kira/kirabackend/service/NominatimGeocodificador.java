package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository.Coordenadas;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.text.Normalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Consulta o Nominatim (OpenStreetMap). Regras do serviço público: identificar o sistema no
 * User-Agent e fazer no máximo uma consulta por segundo.
 *
 * Quando não acha o endereço, o Nominatim pode devolver outro lugar parecido. Por isso o resultado
 * só vale se a cidade e o estado baterem: sem distância é melhor do que distância errada.
 *
 * O endereço nunca vai para o log, e complemento e bairro não são enviados.
 */
@Service
public class NominatimGeocodificador implements Geocodificador {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocodificador.class);

    private static final String BUSCA = "/search?format=jsonv2&limit=5&countrycodes=br&addressdetails=1"
            + "&street={rua}&city={cidade}&state={estado}";
    private static final List<String> CAMPOS_DE_CIDADE = List.of("city", "town", "municipality", "village");
    private static final String CAMPO_DE_ESTADO = "ISO3166-2-lvl4";
    private static final int DIGITOS_DO_CEP = 8;

    private final RestClient restClient;
    private final String baseUrl;
    private final String userAgent;
    private final boolean ativa;
    private final long intervaloNanos;
    private long proximaConsultaNanos = System.nanoTime();

    @Autowired
    public NominatimGeocodificador(
            RestClient.Builder builder,
            @Value("${kira.geocodificacao.base-url}") String baseUrl,
            @Value("${kira.geocodificacao.user-agent}") String userAgent,
            @Value("${kira.geocodificacao.ativa:true}") boolean ativa,
            @Value("${kira.geocodificacao.intervalo-ms:1100}") long intervaloMs,
            @Value("${kira.geocodificacao.timeout-conexao-ms:2000}") long timeoutConexaoMs,
            @Value("${kira.geocodificacao.timeout-leitura-ms:3000}") long timeoutLeituraMs) {
        this(criarRestClient(builder, timeoutConexaoMs, timeoutLeituraMs), baseUrl, userAgent, ativa, intervaloMs);
    }

    NominatimGeocodificador(RestClient restClient, String baseUrl, String userAgent, boolean ativa, long intervaloMs) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.userAgent = userAgent;
        this.ativa = ativa;
        this.intervaloNanos = Duration.ofMillis(intervaloMs).toNanos();
    }

    @Override
    public Optional<Coordenadas> localizar(Endereco endereco) {
        if (!ativa || vazio(endereco.getLogradouro()) || vazio(endereco.getCidade()) || vazio(endereco.getEstado())) {
            return Optional.empty();
        }
        try {
            Optional<Coordenadas> achadas = vazio(endereco.getNumero())
                    ? Optional.empty()
                    : consultar(endereco.getNumero().trim() + " " + endereco.getLogradouro().trim(), endereco);
            return achadas.isPresent() ? achadas : consultar(endereco.getLogradouro().trim(), endereco);
        } catch (RestClientException e) {
            log.warn("Geocodificação: falha ao falar com o serviço: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<Coordenadas> consultar(String rua, Endereco endereco) {
        aguardarVez();

        Map<String, String> valores = new HashMap<>();
        valores.put("rua", rua);
        valores.put("cidade", endereco.getCidade().trim());
        valores.put("estado", endereco.getEstado().trim());
        String url = baseUrl + BUSCA;
        String digitosDoCep = endereco.getCep() == null ? "" : endereco.getCep().replaceAll("\\D", "");
        if (digitosDoCep.length() == DIGITOS_DO_CEP) {
            url += "&postalcode={cep}";
            valores.put("cep", digitosDoCep.substring(0, 5) + "-" + digitosDoCep.substring(5));
        }

        JsonNode resultados = restClient.get()
                .uri(url, valores)
                .header(HttpHeaders.USER_AGENT, userAgent)
                .retrieve()
                .body(JsonNode.class);
        if (resultados == null) {
            return Optional.empty();
        }
        return StreamSupport.stream(resultados.spliterator(), false)
                .filter(resultado -> mesmaCidadeEEstado(resultado.path("address"), endereco))
                .map(NominatimGeocodificador::lerCoordenadas)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static boolean mesmaCidadeEEstado(JsonNode achado, Endereco endereco) {
        String estado = "BR-" + endereco.getEstado().trim().toUpperCase(Locale.ROOT);
        String cidade = semAcento(endereco.getCidade());
        return estado.equals(achado.path(CAMPO_DE_ESTADO).asText())
                && CAMPOS_DE_CIDADE.stream().anyMatch(campo -> cidade.equals(semAcento(achado.path(campo).asText())));
    }

    private static Optional<Coordenadas> lerCoordenadas(JsonNode resultado) {
        try {
            return Optional.of(new Coordenadas(
                    Double.parseDouble(resultado.path("lat").asText()),
                    Double.parseDouble(resultado.path("lon").asText())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // Uma pessoa por vez: quem chega durante a espera aguarda a sua vez na fila
    private synchronized void aguardarVez() {
        long esperaNanos = proximaConsultaNanos - System.nanoTime();
        if (esperaNanos > 0) {
            try {
                Thread.sleep(Duration.ofNanos(esperaNanos).toMillis() + 1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        proximaConsultaNanos = System.nanoTime() + intervaloNanos;
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean vazio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static RestClient criarRestClient(RestClient.Builder builder, long timeoutConexaoMs, long timeoutLeituraMs) {
        ClientHttpRequestFactorySettings configuracao = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(timeoutConexaoMs))
                .withReadTimeout(Duration.ofMillis(timeoutLeituraMs));
        return builder.requestFactory(ClientHttpRequestFactories.get(configuracao)).build();
    }
}
