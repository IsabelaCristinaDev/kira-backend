package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.enums.DecisaoVerificacaoIa;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.exception.FalhaVerificacaoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoIndisponivelException;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Quem compara o documento com a selfie. Hoje é o kira-verify-ai (serviço Python da Wanessa);
 * trocar o serviço não mexe no resto.
 */
public interface VerificadorDeIdentidade {

    /**
     * @param versoDocumento verso do documento. Pode ser nulo; hoje o serviço trabalha só com a frente
     * @param esperado       dados do cadastro para o serviço conferir com o documento. Cada campo pode ser nulo
     * @throws VerificacaoIndisponivelException se o serviço não responder a tempo ou estiver fora do ar
     * @throws FalhaVerificacaoException        se a chave estiver errada ou a resposta fugir do contrato
     * @throws RegraDeNegocioException          se o serviço recusar a imagem (tamanho ou formato)
     */
    Resultado verificar(Imagem documento, Imagem versoDocumento, Imagem selfie, TipoDocumentoIdentidade tipoDocumento,
                        DadosDoCadastro esperado);

    /**
     * O que o serviço confere no documento. Sem nenhum dado, ele nunca aprova: só manda para revisão.
     * O toString esconde os valores para CPF, nome e nascimento nunca irem parar num log.
     */
    record DadosDoCadastro(String cpf, String nome, LocalDate dataNascimento) {
        @Override
        public String toString() {
            return "DadosDoCadastro[cpf=" + (cpf != null) + ", nome=" + (nome != null)
                    + ", dataNascimento=" + (dataNascimento != null) + "]";
        }
    }

    /** Imagem já validada pelo backend. O tipo vai junto porque o serviço confere o formato. */
    record Imagem(byte[] conteudo, MediaType tipo) {

        // O record compararia só a referência do array: aqui vale o conteúdo da imagem
        @Override
        public boolean equals(Object outro) {
            return outro instanceof Imagem imagem
                    && Arrays.equals(conteudo, imagem.conteudo)
                    && Objects.equals(tipo, imagem.tipo);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(conteudo) + Objects.hashCode(tipo);
        }

        // Só o tamanho e o tipo: os bytes da foto nunca devem ir parar num log
        @Override
        public String toString() {
            return "Imagem[" + (conteudo == null ? 0 : conteudo.length) + " bytes, " + tipo + "]";
        }
    }

    /**
     * O que o backend guarda da resposta: a decisão, os códigos dos motivos e a similaridade do rosto.
     * Os campos lidos pelo OCR nunca entram aqui. confiancaOcr e requestId ficam nulos quando o serviço não manda.
     */
    record Resultado(
            DecisaoVerificacaoIa decisao,
            List<String> motivos,
            BigDecimal similaridade,
            BigDecimal confiancaOcr,
            String requestId) {

        public Resultado {
            motivos = motivos == null ? List.of() : List.copyOf(motivos);
        }
    }
}
