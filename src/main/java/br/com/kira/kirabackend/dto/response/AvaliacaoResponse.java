package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;

import java.time.LocalDateTime;
import java.util.UUID;

public record AvaliacaoResponse(
        UUID id,
        UUID agendamentoId,
        UUID clienteId,
        String nomeCliente,
        UUID empresaId,
        String nomeEmpresa,
        TipoAvaliador tipoAvaliador,
        Integer nota,
        String comentario,
        LocalDateTime dataCriacao
) {
    public static AvaliacaoResponse from(Avaliacao a) {
        return new AvaliacaoResponse(
                a.getId(),
                a.getAgendamento().getId(),
                a.getCliente().getId(),
                a.getCliente().getNome(),
                a.getEmpresa().getId(),
                a.getEmpresa().getNome(),
                a.getTipoAvaliador(),
                a.getNota(),
                a.getComentario(),
                a.getDataCriacao()
        );
    }
}
