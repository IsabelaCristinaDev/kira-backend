package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.enums.DecisaoVerificacaoIa;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.exception.FalhaVerificacaoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoIndisponivelException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Quem compara o documento com a selfie. Hoje é o kira-verify-ai (serviço Python da Wanessa);
 * trocar o serviço não mexe no resto.
 */
public interface VerificadorDeIdentidade {

    /**
     * @param cpfEsperado CPF do cadastro para o serviço conferir com o documento. Pode ser nulo
     * @throws VerificacaoIndisponivelException se o serviço não responder a tempo ou estiver fora do ar
     * @throws FalhaVerificacaoException        se a chave estiver errada ou a resposta fugir do contrato
     * @throws RegraDeNegocioException          se o serviço recusar a imagem (tamanho ou formato)
     */
    Resultado verificar(Imagem documento, Imagem selfie, TipoDocumentoIdentidade tipoDocumento, String cpfEsperado);

    /** Imagem já validada pelo backend. O tipo vai junto porque o serviço confere o formato. */
    record Imagem(byte[] conteudo, MediaType tipo) {
    }

    /** O que o backend guarda da resposta. Os campos lidos pelo OCR nunca entram aqui. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(
            @JsonProperty("status") DecisaoVerificacaoIa decisao,
            @JsonProperty("reasons") List<String> motivos,
            @JsonProperty("similarity") BigDecimal similaridade,
            @JsonProperty("ocr_confidence") BigDecimal confiancaOcr,
            @JsonProperty("request_id") String requestId) {

        public Resultado {
            motivos = motivos == null ? List.of() : List.copyOf(motivos);
        }
    }
}
