package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.entity.Endereco;

public record EnderecoResponse(
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado
) {
    public static EnderecoResponse from(Endereco endereco) {
        if (endereco == null) return null;
        return new EnderecoResponse(
                endereco.getCep(),
                endereco.getLogradouro(),
                endereco.getNumero(),
                endereco.getComplemento(),
                endereco.getBairro(),
                endereco.getCidade(),
                endereco.getEstado()
        );
    }
}