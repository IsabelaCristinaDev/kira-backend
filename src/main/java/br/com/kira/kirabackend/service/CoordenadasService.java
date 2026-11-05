package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository.Coordenadas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Decide quais coordenadas ficam salvas, porque é delas que sai a distância na busca.
 * Estúdio: vale o endereço, porque o dono pode ativar a localização longe do estúdio; o GPS é só a reserva.
 * Cliente: vale o GPS, que é onde ela está; o endereço é a reserva e só sai do sistema nesse caso.
 */
@RequiredArgsConstructor
@Service
public class CoordenadasService {

    private final Geocodificador geocodificador;

    /** Chamar antes de gravar o endereço novo na empresa: a comparação usa o endereço antigo. */
    public void atualizarEstudio(Empresa empresa, Endereco novoEndereco, Double latitude, Double longitude) {
        if (nadaMudou(empresa, novoEndereco, latitude, longitude)) {
            return;
        }
        Optional<Coordenadas> doEndereco = localizar(novoEndereco);
        empresa.setLatitude(doEndereco.map(Coordenadas::latitude).orElse(latitude));
        empresa.setLongitude(doEndereco.map(Coordenadas::longitude).orElse(longitude));
    }

    public void atualizarCliente(Cliente cliente, Endereco endereco, Double latitude, Double longitude) {
        if (latitude != null && longitude != null) {
            cliente.setLatitude(latitude);
            cliente.setLongitude(longitude);
            return;
        }
        Optional<Coordenadas> doEndereco = localizar(endereco);
        cliente.setLatitude(doEndereco.map(Coordenadas::latitude).orElse(null));
        cliente.setLongitude(doEndereco.map(Coordenadas::longitude).orElse(null));
    }

    // Evita consultar o serviço externo quando o estúdio só editou outro campo do perfil
    private static boolean nadaMudou(Empresa empresa, Endereco novoEndereco, Double latitude, Double longitude) {
        return empresa.getLatitude() != null
                && empresa.getLongitude() != null
                && empresa.getLatitude().equals(latitude)
                && empresa.getLongitude().equals(longitude)
                && mesmoLugar(empresa.getEndereco(), novoEndereco);
    }

    // Complemento e bairro não entram: não mudam o ponto que o serviço devolve
    private static boolean mesmoLugar(Endereco a, Endereco b) {
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getCep(), b.getCep())
                && Objects.equals(a.getLogradouro(), b.getLogradouro())
                && Objects.equals(a.getNumero(), b.getNumero())
                && Objects.equals(a.getCidade(), b.getCidade())
                && Objects.equals(a.getEstado(), b.getEstado());
    }

    private Optional<Coordenadas> localizar(Endereco endereco) {
        return endereco != null ? geocodificador.localizar(endereco) : Optional.empty();
    }
}
