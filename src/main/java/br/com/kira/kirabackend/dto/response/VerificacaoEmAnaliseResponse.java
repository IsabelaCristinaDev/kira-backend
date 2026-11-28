package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Item da fila de revisão manual do admin. Leva só o resultado do serviço e o nome da conta:
 * CPF e imagens não são guardados nem expostos.
 */
public record VerificacaoEmAnaliseResponse(
        UUID id,
        UUID usuarioId,
        String nome,
        TipoUsuario tipoUsuario,
        TipoDocumentoIdentidade tipoDocumento,
        List<String> motivos,
        BigDecimal similaridade,
        BigDecimal confiancaOcr,
        String requestId,
        LocalDateTime dataCriacao) {
}
