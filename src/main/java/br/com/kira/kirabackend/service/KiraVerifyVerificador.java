package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.config.ClientesHttpConfig;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.exception.FalhaVerificacaoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoIndisponivelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;

/**
 * Chama o kira-verify-ai em POST /v1/verify: multipart com document, selfie, document_type e,
 * se houver, expected_cpf. Toda chamada leva o header X-Service-Key.
 *
 * Tenta de novo no máximo uma vez, e só quando o serviço não chegou a receber o pedido (conexão
 * recusada) ou disse que está ocupado (503). Erro 4xx não se repete: o resultado seria o mesmo.
 * Timeout de leitura também não se repete, para o app não esperar o dobro.
 *
 * Imagens, CPF e a chave nunca vão para o log: só o status HTTP e o request_id.
 */
@Service
public class KiraVerifyVerificador implements VerificadorDeIdentidade {

    private static final Logger log = LoggerFactory.getLogger(KiraVerifyVerificador.class);

    static final String CABECALHO_CHAVE = "X-Service-Key";
    private static final String CAMINHO = "/v1/verify";
    private static final int MAXIMO_TENTATIVAS = 2;

    private final RestClient restClient;
    private final String baseUrl;
    private final String chaveServico;

    // Construtor único: o Spring usa este sem precisar de @Autowired, e os testes passam um RestClient simulado
    KiraVerifyVerificador(
            @Qualifier(ClientesHttpConfig.VERIFICACAO) RestClient restClient,
            @Value("${kira.verificacao.servico.base-url}") String baseUrl,
            @Value("${kira.verificacao.servico.chave:}") String chaveServico) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.chaveServico = chaveServico;
    }

    @Override
    public Resultado verificar(Imagem documento, Imagem selfie, TipoDocumentoIdentidade tipoDocumento,
                               String cpfEsperado) {
        if (chaveServico.isBlank()) {
            log.error("Verificação: VERIFY_SERVICE_KEY não configurada");
            throw new FalhaVerificacaoException("Chave do serviço de verificação não configurada");
        }

        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("document", parteDeImagem(documento, "document"));
        corpo.add("selfie", parteDeImagem(selfie, "selfie"));
        corpo.add("document_type", tipoDocumento.name());
        if (cpfEsperado != null && !cpfEsperado.isBlank()) {
            corpo.add("expected_cpf", cpfEsperado);
        }

        Resultado resultado = enviarComNovaTentativa(corpo);
        if (resultado == null || resultado.decisao() == null) {
            log.error("Verificação: resposta do serviço sem status");
            throw new FalhaVerificacaoException("O serviço de verificação devolveu uma resposta fora do contrato");
        }
        log.info("Verificação: serviço respondeu {} (request_id {})", resultado.decisao(), resultado.requestId());
        return resultado;
    }

    private Resultado enviarComNovaTentativa(MultiValueMap<String, Object> corpo) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                return enviar(corpo);
            } catch (VerificacaoIndisponivelException e) {
                if (tentativa >= MAXIMO_TENTATIVAS || !valeTentarDeNovo(e)) {
                    throw e;
                }
                log.warn("Verificação: serviço indisponível, tentando de novo");
            }
        }
    }

    private Resultado enviar(MultiValueMap<String, Object> corpo) {
        try {
            return restClient.post()
                    .uri(baseUrl + CAMINHO)
                    .header(CABECALHO_CHAVE, chaveServico)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(corpo)
                    .retrieve()
                    .body(Resultado.class);
        } catch (RestClientResponseException e) {
            throw traduzirErroHttp(e);
        } catch (ResourceAccessException e) {
            // Timeout ou falha de rede: o pedido não teve resposta
            log.warn("Verificação: sem resposta do serviço ({})", nomeDaCausa(e));
            throw new VerificacaoIndisponivelException("O serviço de verificação não respondeu", e);
        } catch (RestClientException e) {
            // Corpo que não vira Resultado, como um status fora do contrato
            log.error("Verificação: resposta do serviço fora do contrato ({})", e.getClass().getSimpleName());
            throw new FalhaVerificacaoException("O serviço de verificação devolveu uma resposta fora do contrato", e);
        }
    }

    // O corpo da resposta de erro não vai para o log: pode repetir dados enviados
    private static RuntimeException traduzirErroHttp(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            log.warn("Verificação: serviço respondeu 503");
            return new VerificacaoIndisponivelException("O serviço de verificação está ocupado", e);
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            log.error("Verificação: serviço recusou a chave (401). Confira VERIFY_SERVICE_KEY nos dois serviços");
            return new FalhaVerificacaoException("Chave do serviço de verificação recusada", e);
        }
        if (status == HttpStatus.PAYLOAD_TOO_LARGE.value() || status == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
            // Não deveria acontecer: o backend valida tamanho e formato antes de chamar
            log.warn("Verificação: serviço recusou a imagem ({})", status);
            return new RegraDeNegocioException("A imagem precisa ser JPG ou PNG de até 5 MB");
        }
        log.error("Verificação: serviço respondeu {}", status);
        return new FalhaVerificacaoException("O serviço de verificação recusou a solicitação", e);
    }

    // Conexão recusada: o pedido nem chegou ao serviço. 503: o serviço pediu para tentar de novo
    private static boolean valeTentarDeNovo(VerificacaoIndisponivelException e) {
        Throwable causa = e.getCause();
        if (causa instanceof RestClientResponseException) {
            return true;
        }
        return causa != null && causa.getCause() instanceof ConnectException;
    }

    private static HttpEntity<Resource> parteDeImagem(Imagem imagem, String nome) {
        String extensao = MediaType.IMAGE_PNG.equalsTypeAndSubtype(imagem.tipo()) ? ".png" : ".jpg";
        Resource arquivo = new ByteArrayResource(imagem.conteudo()) {
            @Override
            public String getFilename() {
                return nome + extensao;
            }
        };
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(imagem.tipo());
        return new HttpEntity<>(arquivo, cabecalhos);
    }

    private static String nomeDaCausa(Exception e) {
        Throwable causa = e.getCause() != null ? e.getCause() : e;
        return causa.getClass().getSimpleName();
    }
}
