package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.config.ClientesHttpConfig;
import br.com.kira.kirabackend.domain.enums.DecisaoVerificacaoIa;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.exception.FalhaVerificacaoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoIndisponivelException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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

import java.math.BigDecimal;
import java.net.ConnectException;
import java.util.List;
import java.util.Objects;

/**
 * Chama o kira-verify-ai (módulo do kira-ai) em POST /verify/identity: multipart com document_image,
 * selfie_image e os dados do cadastro que houver (expected_cpf, expected_name, expected_birth_date).
 * O verso do documento não é enviado: o serviço trabalha só com a frente. O header X-Service-Key só
 * vai quando a chave estiver configurada; o kira-ai de hoje não pede chave.
 *
 * Tenta de novo no máximo uma vez, e só quando o serviço não chegou a receber o pedido (conexão
 * recusada) ou disse que está ocupado (503). Erro 4xx não se repete: o resultado seria o mesmo.
 * Timeout de leitura também não se repete, para o app não esperar o dobro.
 *
 * Imagens, dados do cadastro e a chave nunca vão para o log: só o status HTTP e a decisão.
 */
@Service
public class KiraVerifyVerificador implements VerificadorDeIdentidade {

    private static final Logger log = LoggerFactory.getLogger(KiraVerifyVerificador.class);

    static final String CABECALHO_CHAVE = "X-Service-Key";
    private static final String CAMINHO = "/verify/identity";
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
    public Resultado verificar(Imagem documento, Imagem versoDocumento, Imagem selfie,
                               TipoDocumentoIdentidade tipoDocumento, DadosDoCadastro esperado) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("document_image", parteDeImagem(documento, "document_image"));
        corpo.add("selfie_image", parteDeImagem(selfie, "selfie_image"));
        if (esperado != null) {
            adicionarSePreenchido(corpo, "expected_cpf", esperado.cpf());
            adicionarSePreenchido(corpo, "expected_name", esperado.nome());
            if (esperado.dataNascimento() != null) {
                corpo.add("expected_birth_date", esperado.dataNascimento().toString());
            }
        }

        Resultado resultado = enviarComNovaTentativa(corpo);
        if (resultado == null || resultado.decisao() == null) {
            log.error("Verificação: resposta do serviço sem status");
            throw new FalhaVerificacaoException("O serviço de verificação devolveu uma resposta fora do contrato");
        }
        log.info("Verificação: serviço respondeu {}", resultado.decisao());
        return resultado;
    }

    private static void adicionarSePreenchido(MultiValueMap<String, Object> corpo, String campo, String valor) {
        if (valor != null && !valor.isBlank()) {
            corpo.add(campo, valor);
        }
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
            RespostaServico resposta = restClient.post()
                    .uri(baseUrl + CAMINHO)
                    .headers(cabecalhos -> {
                        if (!chaveServico.isBlank()) {
                            cabecalhos.set(CABECALHO_CHAVE, chaveServico);
                        }
                    })
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(corpo)
                    .retrieve()
                    .body(RespostaServico.class);
            return resposta == null ? null : resposta.paraResultado();
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

    /** Resposta do POST /verify/identity. Só o que o backend usa; o resto (OCR, documento) é ignorado. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaServico(
            @JsonProperty("status") DecisaoVerificacaoIa status,
            @JsonProperty("reasons") List<Motivo> reasons,
            @JsonProperty("face") Rosto face) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Motivo(@JsonProperty("code") String code) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Rosto(@JsonProperty("similarity") BigDecimal similarity) {
        }

        Resultado paraResultado() {
            List<String> motivos = reasons == null ? List.of()
                    : reasons.stream().filter(Objects::nonNull).map(Motivo::code).filter(Objects::nonNull).toList();
            BigDecimal similaridade = face == null ? null : face.similarity();
            return new Resultado(status, motivos, similaridade, null, null);
        }
    }

    private static String nomeDaCausa(Exception e) {
        Throwable causa = e.getCause() != null ? e.getCause() : e;
        return causa.getClass().getSimpleName();
    }
}
