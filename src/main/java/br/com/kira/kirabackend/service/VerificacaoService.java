package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.entity.VerificacaoIdentidade;
import br.com.kira.kirabackend.domain.enums.DecisaoVerificacaoIa;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.dto.response.VerificacaoResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Imagem;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Resultado;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Verificação de identidade: recebe documento e selfie, repassa ao serviço de IA e guarda só o resultado.
 *
 * As fotos só passam pela memória: são lidas, enviadas ao serviço e descartadas. Os logs levam só o id da
 * verificação, o request_id e o status, nunca CPF, nome ou imagem.
 *
 * O resultado fica na tentativa mais recente: "perfil verificado" é ter a última tentativa como VERIFIED.
 */
@Service
public class VerificacaoService {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoService.class);

    private static final long BYTES_POR_MB = 1024L * 1024L;

    private final VerificacaoIdentidadeRepository repository;
    private final VerificadorDeIdentidade verificador;
    private final String versaoConsentimento;
    private final long tamanhoMaximoBytes;
    private final int limiteTentativas;

    public VerificacaoService(
            VerificacaoIdentidadeRepository repository,
            VerificadorDeIdentidade verificador,
            @Value("${kira.verificacao.versao-consentimento:v1}") String versaoConsentimento,
            @Value("${kira.verificacao.tamanho-maximo-bytes:5242880}") long tamanhoMaximoBytes,
            @Value("${kira.verificacao.limite-tentativas:3}") int limiteTentativas) {
        this.repository = repository;
        this.verificador = verificador;
        this.versaoConsentimento = versaoConsentimento;
        this.tamanhoMaximoBytes = tamanhoMaximoBytes;
        this.limiteTentativas = limiteTentativas;
    }

    public VerificacaoResponse consultar(UUID usuarioId) {
        int restantes = tentativasRestantes(usuarioId);
        return repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(usuarioId)
                .map(verificacao -> new VerificacaoResponse(verificacao.getStatus(), verificacao.getMotivos(), restantes))
                .orElseGet(() -> new VerificacaoResponse(null, List.of(), restantes));
    }

    // Sem @Transactional de propósito: o serviço de IA pode levar até 30 s e não deve segurar uma conexão do banco
    public VerificacaoResponse enviar(Usuario usuario, MultipartFile documento, MultipartFile selfie,
                                      String tipoDocumento, Boolean consentimento) {
        TipoUsuario tipoUsuario = tipoDoUsuario(usuario);
        if (!Boolean.TRUE.equals(consentimento)) {
            throw new RegraDeNegocioException("Aceite o termo de consentimento para verificar sua identidade");
        }
        TipoDocumentoIdentidade tipo = lerTipoDocumento(tipoDocumento);
        Imagem imagemDocumento = lerImagem(documento, "Envie a foto do documento");
        Imagem imagemSelfie = lerImagem(selfie, "Envie a selfie");

        VerificacaoIdentidade verificacao = prepararTentativa(usuario, tipoUsuario, tipo);

        // Se o serviço falhar, a exceção sobe e a tentativa continua PENDING para a pessoa tentar de novo
        Resultado resultado = verificador.verificar(imagemDocumento, imagemSelfie, tipo, cpfDoCadastro(usuario));

        verificacao.setStatus(statusDa(resultado.decisao()));
        verificacao.setMotivos(resultado.motivos());
        verificacao.setSimilaridade(resultado.similaridade());
        verificacao.setConfiancaOcr(resultado.confiancaOcr());
        verificacao.setRequestId(resultado.requestId());
        verificacao = repository.save(verificacao);
        log.info("Verificação {}: {} (request_id {})", verificacao.getId(), verificacao.getStatus(), verificacao.getRequestId());

        return new VerificacaoResponse(verificacao.getStatus(), verificacao.getMotivos(), tentativasRestantes(usuario.getId()));
    }

    // Confere a situação atual e grava a tentativa como PENDING antes de chamar o serviço
    private VerificacaoIdentidade prepararTentativa(Usuario usuario, TipoUsuario tipoUsuario, TipoDocumentoIdentidade tipo) {
        VerificacaoIdentidade atual = repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(usuario.getId()).orElse(null);
        if (atual != null && atual.getStatus() == StatusVerificacao.VERIFIED) {
            throw new RegraDeNegocioException("Sua identidade já foi verificada");
        }
        if (atual != null && atual.getStatus() == StatusVerificacao.IN_REVIEW) {
            throw new RegraDeNegocioException("Sua verificação está em análise. Aguarde o resultado");
        }
        if (tentativasRestantes(usuario.getId()) <= 0) {
            throw new RegraDeNegocioException("Você usou todas as " + limiteTentativas + " tentativas de verificação");
        }

        // Tentativa que ficou sem resposta do serviço é reaproveitada: não conta como uma nova
        VerificacaoIdentidade verificacao = atual != null && atual.getStatus() == StatusVerificacao.PENDING
                ? atual
                : new VerificacaoIdentidade();
        verificacao.setUsuario(usuario);
        verificacao.setTipoUsuario(tipoUsuario);
        verificacao.setTipoDocumento(tipo);
        verificacao.setStatus(StatusVerificacao.PENDING);
        verificacao.setVersaoConsentimento(versaoConsentimento);
        verificacao.setConsentimentoAceitoEm(LocalDateTime.now(KiraTimeZone.DEFAULT));
        return repository.save(verificacao);
    }

    private int tentativasRestantes(UUID usuarioId) {
        long usadas = repository.countByUsuarioIdAndStatusNot(usuarioId, StatusVerificacao.PENDING);
        return (int) Math.max(0, limiteTentativas - usadas);
    }

    private static StatusVerificacao statusDa(DecisaoVerificacaoIa decisao) {
        return switch (decisao) {
            case APPROVED -> StatusVerificacao.VERIFIED;
            case REVIEW -> StatusVerificacao.IN_REVIEW;
            case REJECTED -> StatusVerificacao.REJECTED;
        };
    }

    private static TipoUsuario tipoDoUsuario(Usuario usuario) {
        if (usuario instanceof Cliente) {
            return TipoUsuario.CLIENTE;
        }
        if (usuario instanceof Empresa) {
            return TipoUsuario.EMPRESA;
        }
        // Admin não tem cadastro de cliente nem de profissional para verificar
        throw new RegraDeNegocioException(usuario instanceof Admin
                ? "Contas de administrador não fazem verificação de identidade"
                : "Este tipo de conta não faz verificação de identidade");
    }

    // O serviço confere o CPF do documento com o do cadastro. Estabelecimento com CNPJ não tem CPF para conferir
    private static String cpfDoCadastro(Usuario usuario) {
        if (usuario instanceof Cliente cliente) {
            return cliente.getCpf();
        }
        if (usuario instanceof Empresa empresa) {
            return empresa.getCpf();
        }
        return null;
    }

    private static TipoDocumentoIdentidade lerTipoDocumento(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new RegraDeNegocioException("Informe o tipo do documento: CNH ou RG");
        }
        try {
            return TipoDocumentoIdentidade.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RegraDeNegocioException("Tipo de documento inválido. Use CNH ou RG");
        }
    }

    private Imagem lerImagem(MultipartFile arquivo, String mensagemAusente) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException(mensagemAusente);
        }
        if (arquivo.getSize() > tamanhoMaximoBytes) {
            throw new RegraDeNegocioException("A foto deve ter no máximo " + (tamanhoMaximoBytes / BYTES_POR_MB) + " MB");
        }

        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new RegraDeNegocioException("Não foi possível ler a foto enviada");
        }

        // O tipo informado pelo app não é confiável: o formato é conferido pelos primeiros bytes
        if (ehJpeg(bytes)) {
            return new Imagem(bytes, MediaType.IMAGE_JPEG);
        }
        if (ehPng(bytes)) {
            return new Imagem(bytes, MediaType.IMAGE_PNG);
        }
        throw new RegraDeNegocioException("Formato de foto não suportado. Use JPG ou PNG");
    }

    private static boolean ehJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean ehPng(byte[] b) {
        return b.length > 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }
}
