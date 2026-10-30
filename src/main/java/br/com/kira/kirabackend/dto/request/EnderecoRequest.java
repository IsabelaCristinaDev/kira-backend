package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.entity.Endereco;

public record EnderecoRequest(
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
        endereco.setCep(cep);
        endereco.setLogradouro(logradouro);
        endereco.setNumero(numero);
        endereco.setComplemento(complemento);
        endereco.setBairro(bairro);
        endereco.setCidade(cidade);
        endereco.setEstado(estado);
        return endereco;
    }
}
