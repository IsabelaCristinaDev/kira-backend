package br.com.kira.kirabackend.dto.response;

import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Situação da verificação de identidade. Os nomes dos campos seguem o contrato combinado com o app.
 *
 * @param status              nulo enquanto a pessoa nunca enviou fotos
 * @param motivos             códigos exatamente como o serviço devolveu (o app mostra uma mensagem por código)
 * @param tentativasRestantes quantos envios a pessoa ainda pode fazer
 * @param obrigatoria         se agendar e aparecer na vitrine exigem a verificação (VERIFICACAO_OBRIGATORIA)
 */
public record VerificacaoResponse(
        @JsonProperty("status") StatusVerificacao status,
        @JsonProperty("reasons") List<String> motivos,
        @JsonProperty("attempts_left") int tentativasRestantes,
        @JsonProperty("required") boolean obrigatoria) {
}
