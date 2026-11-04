package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.entity.ContatoEmergencia;

import java.util.UUID;

public record ContatoEmergenciaResponse(
        UUID id,
        String nome,
        String telefone,
        String parentesco
) {
    public static ContatoEmergenciaResponse from(ContatoEmergencia contato) {
        return new ContatoEmergenciaResponse(
                contato.getId(), contato.getNome(), contato.getTelefone(), contato.getParentesco());
    }
}
