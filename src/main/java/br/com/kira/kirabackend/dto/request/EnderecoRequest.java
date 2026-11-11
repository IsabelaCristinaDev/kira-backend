package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.entity.Endereco;
import jakarta.validation.constraints.Pattern;

public record EnderecoRequest(
        // Aceita "74115060", "74115-060" ou "74.115-060": o que vale são os 8 dígitos. Em branco continua opcional
        @Pattern(regexp = "^\\s*$|^[\\s.-]*(\\d[\\s.-]*){8}$", message = "CEP deve ter 8 dígitos")
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado
) {
    public Endereco paraEndereco() {
        Endereco endereco = new Endereco();
        endereco.setCep(soDigitos(cep));
        endereco.setLogradouro(logradouro);
        endereco.setNumero(numero);
        endereco.setComplemento(complemento);
        endereco.setBairro(bairro);
        endereco.setCidade(cidade);
        endereco.setEstado(estado);
        return endereco;
    }

    // A coluna guarda só os 8 dígitos
    private static String soDigitos(String cep) {
        if (cep == null || cep.isBlank()) {
            return null;
        }
        return cep.replaceAll("\\D", "");
    }
}
