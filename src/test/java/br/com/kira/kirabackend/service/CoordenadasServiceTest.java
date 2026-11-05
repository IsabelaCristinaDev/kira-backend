package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository.Coordenadas;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoordenadasServiceTest {

    private static final Coordenadas DO_ENDERECO = new Coordenadas(-16.68, -49.25);
    private static final double LAT_GPS = -16.70;
    private static final double LNG_GPS = -49.30;

    @Mock
    private Geocodificador geocodificador;

    @InjectMocks
    private CoordenadasService service;

    private static Endereco endereco(String numero) {
        return new Endereco("74000000", "Avenida Goiás", numero, "Sala 2", "Centro", "Goiânia", "GO");
    }

    @Nested
    @DisplayName("Estúdio: o endereço manda")
    class Estudio {

        @Test
        @DisplayName("Sem coordenadas guardadas, usa as do endereço e ignora o GPS enviado")
        void enderecoVenceOGps() {
            Empresa empresa = new Empresa();
            Endereco novo = endereco("10");
            when(geocodificador.localizar(novo)).thenReturn(Optional.of(DO_ENDERECO));

            service.atualizarEstudio(empresa, novo, LAT_GPS, LNG_GPS);

            assertEquals(-16.68, empresa.getLatitude());
            assertEquals(-49.25, empresa.getLongitude());
        }

        @Test
        @DisplayName("Endereço não encontrado: fica com o GPS enviado")
        void naoEncontradoUsaOGps() {
            Empresa empresa = new Empresa();
            when(geocodificador.localizar(any())).thenReturn(Optional.empty());

            service.atualizarEstudio(empresa, endereco("10"), LAT_GPS, LNG_GPS);

            assertEquals(LAT_GPS, empresa.getLatitude());
            assertEquals(LNG_GPS, empresa.getLongitude());
        }

        @Test
        @DisplayName("Endereço não encontrado e sem GPS: fica sem coordenadas, sem manter as antigas")
        void naoEncontradoSemGps() {
            Empresa empresa = new Empresa();
            empresa.setEndereco(endereco("10"));
            empresa.setLatitude(-1.0);
            empresa.setLongitude(-2.0);
            when(geocodificador.localizar(any())).thenReturn(Optional.empty());

            service.atualizarEstudio(empresa, endereco("99"), null, null);

            assertNull(empresa.getLatitude());
            assertNull(empresa.getLongitude());
        }

        @Test
        @DisplayName("Mesmo endereço e mesmas coordenadas: não consulta o serviço de novo")
        void nadaMudou() {
            Empresa empresa = new Empresa();
            empresa.setEndereco(endereco("10"));
            empresa.setLatitude(-16.68);
            empresa.setLongitude(-49.25);

            service.atualizarEstudio(empresa, endereco("10"), -16.68, -49.25);

            verifyNoInteractions(geocodificador);
            assertEquals(-16.68, empresa.getLatitude());
            assertEquals(-49.25, empresa.getLongitude());
        }

        @Test
        @DisplayName("Trocar só o complemento não muda o lugar: não consulta o serviço")
        void complementoNaoMudaOLugar() {
            Empresa empresa = new Empresa();
            empresa.setEndereco(endereco("10"));
            empresa.setLatitude(-16.68);
            empresa.setLongitude(-49.25);
            Endereco novo = endereco("10");
            novo.setComplemento("Sala 9");

            service.atualizarEstudio(empresa, novo, -16.68, -49.25);

            verifyNoInteractions(geocodificador);
        }

        @Test
        @DisplayName("Mudou o número: consulta o endereço novo, mesmo com as coordenadas antigas no pedido")
        void numeroMudou() {
            Empresa empresa = new Empresa();
            empresa.setEndereco(endereco("10"));
            empresa.setLatitude(-1.0);
            empresa.setLongitude(-2.0);
            Endereco novo = endereco("99");
            when(geocodificador.localizar(novo)).thenReturn(Optional.of(DO_ENDERECO));

            service.atualizarEstudio(empresa, novo, -1.0, -2.0);

            assertEquals(-16.68, empresa.getLatitude());
        }

        @Test
        @DisplayName("Mesmo endereço com GPS novo: o endereço corrige a posição")
        void gpsNovoNoMesmoEndereco() {
            Empresa empresa = new Empresa();
            empresa.setEndereco(endereco("10"));
            empresa.setLatitude(-1.0);
            empresa.setLongitude(-2.0);
            Endereco mesmo = endereco("10");
            when(geocodificador.localizar(mesmo)).thenReturn(Optional.of(DO_ENDERECO));

            service.atualizarEstudio(empresa, mesmo, LAT_GPS, LNG_GPS);

            assertEquals(-16.68, empresa.getLatitude());
            assertEquals(-49.25, empresa.getLongitude());
        }

        @Test
        @DisplayName("Sem endereço: guarda o que veio e não consulta o serviço")
        void semEndereco() {
            Empresa empresa = new Empresa();

            service.atualizarEstudio(empresa, null, LAT_GPS, LNG_GPS);

            verifyNoInteractions(geocodificador);
            assertEquals(LAT_GPS, empresa.getLatitude());
            assertEquals(LNG_GPS, empresa.getLongitude());
        }
    }

    @Nested
    @DisplayName("Cliente: o GPS manda")
    class ClienteNaConta {

        @Test
        @DisplayName("Com GPS: guarda o GPS e não envia o endereço a terceiros")
        void gpsVence() {
            Cliente cliente = new Cliente();

            service.atualizarCliente(cliente, endereco("10"), LAT_GPS, LNG_GPS);

            verifyNoInteractions(geocodificador);
            assertEquals(LAT_GPS, cliente.getLatitude());
            assertEquals(LNG_GPS, cliente.getLongitude());
        }

        @Test
        @DisplayName("Sem GPS: usa o endereço")
        void semGpsUsaOEndereco() {
            Cliente cliente = new Cliente();
            Endereco novo = endereco("10");
            when(geocodificador.localizar(novo)).thenReturn(Optional.of(DO_ENDERECO));

            service.atualizarCliente(cliente, novo, null, null);

            verify(geocodificador).localizar(novo);
            assertEquals(-16.68, cliente.getLatitude());
            assertEquals(-49.25, cliente.getLongitude());
        }

        @Test
        @DisplayName("Sem GPS e endereço não encontrado: apaga as coordenadas antigas")
        void semGpsNaoEncontrado() {
            Cliente cliente = new Cliente();
            cliente.setLatitude(-1.0);
            cliente.setLongitude(-2.0);
            when(geocodificador.localizar(any())).thenReturn(Optional.empty());

            service.atualizarCliente(cliente, endereco("10"), null, null);

            assertNull(cliente.getLatitude());
            assertNull(cliente.getLongitude());
        }

        @Test
        @DisplayName("Sem GPS e sem endereço: fica sem coordenadas e não consulta o serviço")
        void semNada() {
            Cliente cliente = new Cliente();
            cliente.setLatitude(-1.0);
            cliente.setLongitude(-2.0);

            service.atualizarCliente(cliente, null, null, null);

            verifyNoInteractions(geocodificador);
            assertNull(cliente.getLatitude());
            assertNull(cliente.getLongitude());
        }

        @Test
        @DisplayName("Só a latitude não é posição: usa o endereço")
        void coordenadaPelaMetade() {
            Cliente cliente = new Cliente();
            Endereco novo = endereco("10");
            when(geocodificador.localizar(novo)).thenReturn(Optional.of(DO_ENDERECO));

            service.atualizarCliente(cliente, novo, LAT_GPS, null);

            assertEquals(-16.68, cliente.getLatitude());
            assertEquals(-49.25, cliente.getLongitude());
        }
    }
}
