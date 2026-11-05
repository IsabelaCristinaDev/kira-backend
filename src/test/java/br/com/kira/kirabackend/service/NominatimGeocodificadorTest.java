package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository.Coordenadas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NominatimGeocodificadorTest {

    private static final String BASE = "https://nominatim.exemplo";
    private static final String AGENTE = "KiraTeste/1.0";

    private static final String GOIANIA_GO = """
            [{"lat":"-16.6586311","lon":"-49.2618027","display_name":"Avenida Goiás, Goiânia",
              "address":{"road":"Avenida Goiás","city":"Goiânia","ISO3166-2-lvl4":"BR-GO"}}]""";
    private static final String NENHUM = "[]";

    private RestClient.Builder builder;
    private MockRestServiceServer servidor;
    private NominatimGeocodificador geocodificador;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        geocodificador = new NominatimGeocodificador(builder.build(), BASE, AGENTE, true, 0);
    }

    private static Endereco endereco() {
        return new Endereco("74005010", "Avenida Goiás", "100", "Apto 3", "Centro", "Goiânia", "GO");
    }

    @Test
    @DisplayName("Envia número, rua, cidade, estado e CEP com hífen, se identifica e lê as coordenadas")
    void sucesso() {
        servidor.expect(requestTo(allOf(
                        startsWith(BASE + "/search?"),
                        containsString("street=100%20Avenida%20Goi%C3%A1s"),
                        containsString("city=Goi%C3%A2nia"),
                        containsString("state=GO"),
                        containsString("postalcode=74005-010"),
                        containsString("countrycodes=br"),
                        containsString("addressdetails=1"))))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("User-Agent", AGENTE))
                .andRespond(withSuccess(GOIANIA_GO, MediaType.APPLICATION_JSON));

        Optional<Coordenadas> achadas = geocodificador.localizar(endereco());

        assertEquals(Optional.of(new Coordenadas(-16.6586311, -49.2618027)), achadas);
        servidor.verify();
    }

    @Test
    @DisplayName("O complemento e o bairro não saem do sistema")
    void naoEnviaComplemento() {
        servidor.expect(requestTo(allOf(not(containsString("Apto")), not(containsString("Centro")))))
                .andRespond(withSuccess(GOIANIA_GO, MediaType.APPLICATION_JSON));

        geocodificador.localizar(endereco());

        servidor.verify();
    }

    @Test
    @DisplayName("Número não encontrado: tenta de novo só com a rua")
    void tentaSemNumero() {
        servidor.expect(requestTo(containsString("street=100%20Avenida")))
                .andRespond(withSuccess(NENHUM, MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(containsString("street=Avenida")))
                .andRespond(withSuccess(GOIANIA_GO, MediaType.APPLICATION_JSON));

        Optional<Coordenadas> achadas = geocodificador.localizar(endereco());

        assertEquals(-16.6586311, achadas.orElseThrow().latitude());
        servidor.verify();
    }

    @Test
    @DisplayName("Sem número no endereço: faz uma consulta só")
    void semNumero() {
        Endereco endereco = endereco();
        endereco.setNumero(" ");
        servidor.expect(requestTo(containsString("street=Avenida")))
                .andRespond(withSuccess(NENHUM, MediaType.APPLICATION_JSON));

        assertTrue(geocodificador.localizar(endereco).isEmpty());

        servidor.verify();
    }

    @Test
    @DisplayName("CEP incompleto não vai na consulta")
    void cepIncompleto() {
        Endereco endereco = endereco();
        endereco.setCep("7400");
        servidor.expect(requestTo(not(containsString("postalcode"))))
                .andRespond(withSuccess(GOIANIA_GO, MediaType.APPLICATION_JSON));

        assertTrue(geocodificador.localizar(endereco).isPresent());

        servidor.verify();
    }

    @Test
    @DisplayName("Resultado em outra cidade é descartado, nas duas tentativas")
    void outraCidade() {
        String anapolis = """
                [{"lat":"-16.37","lon":"-48.94",
                  "address":{"road":"Avenida Contorno","city":"Anápolis","ISO3166-2-lvl4":"BR-GO"}}]""";
        servidor.expect(anything()).andRespond(withSuccess(anapolis, MediaType.APPLICATION_JSON));
        servidor.expect(anything()).andRespond(withSuccess(anapolis, MediaType.APPLICATION_JSON));

        assertTrue(geocodificador.localizar(endereco()).isEmpty());

        servidor.verify();
    }

    @Test
    @DisplayName("Cidade com o mesmo nome em outro estado é descartada")
    void outroEstado() {
        String outroEstado = """
                [{"lat":"-9.07","lon":"-44.35",
                  "address":{"road":"Avenida Goiás","city":"Goiânia","ISO3166-2-lvl4":"BR-PI"}}]""";
        servidor.expect(anything()).andRespond(withSuccess(outroEstado, MediaType.APPLICATION_JSON));
        servidor.expect(anything()).andRespond(withSuccess(outroEstado, MediaType.APPLICATION_JSON));

        assertTrue(geocodificador.localizar(endereco()).isEmpty());

        servidor.verify();
    }

    @Test
    @DisplayName("Aceita a cidade em town e sem diferenciar acento ou maiúscula")
    void cidadeEmTownSemAcento() {
        Endereco endereco = endereco();
        endereco.setCidade("PIRENOPOLIS");
        String pirenopolis = """
                [{"lat":"-15.85","lon":"-48.95",
                  "address":{"road":"Rua Principal","village":"Índio","town":"Pirenópolis","ISO3166-2-lvl4":"BR-GO"}}]""";
        servidor.expect(anything()).andRespond(withSuccess(pirenopolis, MediaType.APPLICATION_JSON));

        assertEquals(-15.85, geocodificador.localizar(endereco).orElseThrow().latitude());

        servidor.verify();
    }

    @Test
    @DisplayName("Faltando rua, cidade ou estado: não consulta nada")
    void enderecoIncompleto() {
        Endereco semRua = endereco();
        semRua.setLogradouro(null);
        Endereco semCidade = endereco();
        semCidade.setCidade(" ");
        Endereco semEstado = endereco();
        semEstado.setEstado(null);

        assertTrue(geocodificador.localizar(semRua).isEmpty());
        assertTrue(geocodificador.localizar(semCidade).isEmpty());
        assertTrue(geocodificador.localizar(semEstado).isEmpty());

        servidor.verify();
    }

    @Test
    @DisplayName("Serviço fora do ar não derruba o cadastro nem repete a consulta")
    void servicoFora() {
        servidor.expect(anything()).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertTrue(geocodificador.localizar(endereco()).isEmpty());

        servidor.verify();
    }

    @Test
    @DisplayName("Resposta sem latitude válida conta como não encontrado")
    void respostaInvalida() {
        String semCoordenada = """
                [{"lat":"abc","lon":"","address":{"city":"Goiânia","ISO3166-2-lvl4":"BR-GO"}}]""";
        servidor.expect(anything()).andRespond(withSuccess(semCoordenada, MediaType.APPLICATION_JSON));
        servidor.expect(anything()).andRespond(withSuccess(semCoordenada, MediaType.APPLICATION_JSON));

        assertTrue(geocodificador.localizar(endereco()).isEmpty());

        servidor.verify();
    }

    @Test
    @DisplayName("Desligado: não envia o endereço para fora")
    void desligado() {
        NominatimGeocodificador desligado = new NominatimGeocodificador(builder.build(), BASE, AGENTE, false, 0);

        assertTrue(desligado.localizar(endereco()).isEmpty());

        servidor.verify();
    }

    @Test
    @DisplayName("Espera o intervalo entre uma consulta e outra, como o Nominatim exige")
    void respeitaIntervalo() {
        NominatimGeocodificador comIntervalo = new NominatimGeocodificador(builder.build(), BASE, AGENTE, true, 200);
        servidor.expect(anything()).andRespond(withSuccess(GOIANIA_GO, MediaType.APPLICATION_JSON));
        servidor.expect(anything()).andRespond(withSuccess(GOIANIA_GO, MediaType.APPLICATION_JSON));

        long inicio = System.nanoTime();
        comIntervalo.localizar(endereco());
        comIntervalo.localizar(endereco());
        long passadosMs = (System.nanoTime() - inicio) / 1_000_000;

        assertTrue(passadosMs >= 200, "passaram só " + passadosMs + " ms");
        servidor.verify();
    }
}
