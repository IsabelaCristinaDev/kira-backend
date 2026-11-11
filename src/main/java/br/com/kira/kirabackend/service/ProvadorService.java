package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.ConsentimentoProvador;
import br.com.kira.kirabackend.dto.response.ConsentimentoProvadorResponse;
import br.com.kira.kirabackend.dto.response.GeracaoProvadorResponse;
import br.com.kira.kirabackend.exception.ConsentimentoProvadorNecessarioException;
import br.com.kira.kirabackend.exception.GeracaoProvadorException;
import br.com.kira.kirabackend.exception.LimiteProvadorException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.ConsentimentoProvadorRepository;
import br.com.kira.kirabackend.repository.UsoProvadorRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Provador virtual: consentimento LGPD e geração da prévia.
 *
 * As fotos só passam pela memória: são enviadas ao gerador e descartadas. Nada é gravado
 * em disco nem no banco, então não há foto para apagar quando o consentimento é revogado.
 */
@Service
public class ProvadorService {

    private static final Logger log = LoggerFactory.getLogger(ProvadorService.class);

    private static final long BYTES_POR_MB = 1024L * 1024L;

    private final ConsentimentoProvadorRepository consentimentoRepository;
    private final UsoProvadorRepository usoRepository;
    private final GeradorDeImagem geradorDeImagem;
    private final String versaoAtual;
    private final long tamanhoMaximoBytes;
    private final int limiteDiario;

    public ProvadorService(
            ConsentimentoProvadorRepository consentimentoRepository,
            UsoProvadorRepository usoRepository,
            GeradorDeImagem geradorDeImagem,
            @Value("${kira.provador.versao-consentimento:v1}") String versaoAtual,
            @Value("${kira.provador.tamanho-maximo-bytes:5242880}") long tamanhoMaximoBytes,
            @Value("${kira.provador.limite-diario:5}") int limiteDiario) {
        this.consentimentoRepository = consentimentoRepository;
        this.usoRepository = usoRepository;
        this.geradorDeImagem = geradorDeImagem;
        this.versaoAtual = versaoAtual;
        this.tamanhoMaximoBytes = tamanhoMaximoBytes;
        this.limiteDiario = limiteDiario;
    }

    public ConsentimentoProvadorResponse consultarConsentimento(UUID usuarioId) {
        return consentimentoRepository.findByUsuarioId(usuarioId)
                .map(this::toResponse)
                .orElseGet(() -> new ConsentimentoProvadorResponse(false, null, null));
    }

    @Transactional
    public ConsentimentoProvadorResponse aceitarConsentimento(UUID usuarioId, String versao) {
        if (!versaoAtual.equals(versao)) {
            throw new RegraDeNegocioException("O termo foi atualizado. Leia a versão mais recente e aceite de novo");
        }

        ConsentimentoProvador consentimento = consentimentoRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> {
                    ConsentimentoProvador novo = new ConsentimentoProvador();
                    novo.setUsuarioId(usuarioId);
                    return novo;
                });
        consentimento.setVersao(versao);
        consentimento.setAceitoEm(LocalDateTime.now(KiraTimeZone.DEFAULT));
        consentimento.setRevogadoEm(null);

        return toResponse(consentimentoRepository.save(consentimento));
    }

    @Transactional
    public ConsentimentoProvadorResponse revogarConsentimento(UUID usuarioId) {
        return consentimentoRepository.findByUsuarioId(usuarioId)
                .map(consentimento -> {
                    if (consentimento.getRevogadoEm() == null) {
                        consentimento.setRevogadoEm(LocalDateTime.now(KiraTimeZone.DEFAULT));
                    }
                    return toResponse(consentimentoRepository.save(consentimento));
                })
                .orElseGet(() -> new ConsentimentoProvadorResponse(false, null, null));
    }

    // Sem @Transactional de propósito: a chamada ao gerador demora e não deve segurar uma conexão do banco
    public GeracaoProvadorResponse gerar(UUID usuarioId, MultipartFile fotoCliente, MultipartFile fotoReferencia) {
        exigirConsentimentoAtivo(usuarioId);

        byte[] cliente = lerImagem(fotoCliente, "Envie a sua foto");
        byte[] referencia = lerImagem(fotoReferencia, "Envie a foto de referência");

        // Reserva antes de chamar o gerador, para pedidos ao mesmo tempo não passarem do limite
        LocalDate hoje = LocalDate.now(KiraTimeZone.DEFAULT);
        if (usoRepository.reservar(usuarioId, hoje, limiteDiario) == 0) {
            throw new LimiteProvadorException(limiteDiario);
        }

        try {
            byte[] previa = geradorDeImagem.gerar(cliente, referencia);
            return new GeracaoProvadorResponse(montarDataUri(previa), LocalDateTime.now(KiraTimeZone.DEFAULT));
        } catch (RuntimeException e) {
            // Sem prévia, a tentativa não conta para a pessoa
            usoRepository.devolver(usuarioId, hoje);
            throw e;
        }
    }

    // O modelo escolhe o formato da saída, então o tipo informado ao app vem dos primeiros bytes da prévia
    private static String montarDataUri(byte[] previa) {
        String tipo;
        if (ehJpeg(previa)) {
            tipo = "image/jpeg";
        } else if (ehPng(previa)) {
            tipo = "image/png";
        } else {
            log.error("Provador: o gerador devolveu uma prévia em formato desconhecido");
            throw new GeracaoProvadorException("A prévia devolvida pelo gerador tem formato desconhecido");
        }
        return "data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(previa);
    }

    private void exigirConsentimentoAtivo(UUID usuarioId) {
        boolean ativo = consentimentoRepository.findByUsuarioId(usuarioId)
                .map(consentimento -> consentimento.estaAtivo(versaoAtual))
                .orElse(false);
        if (!ativo) {
            throw new ConsentimentoProvadorNecessarioException();
        }
    }

    private byte[] lerImagem(MultipartFile arquivo, String mensagemAusente) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraDeNegocioException(mensagemAusente);
        }
        if (arquivo.getSize() > tamanhoMaximoBytes) {
            throw new RegraDeNegocioException(
                    "A foto deve ter no máximo " + (tamanhoMaximoBytes / BYTES_POR_MB) + " MB");
        }

        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new RegraDeNegocioException("Não foi possível ler a foto enviada");
        }

        // O tipo informado pelo app não é confiável: o formato é conferido pelos primeiros bytes
        if (!ehJpeg(bytes) && !ehPng(bytes)) {
            throw new RegraDeNegocioException("Formato de foto não suportado. Use JPG ou PNG");
        }
        return bytes;
    }

    private static boolean ehJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean ehPng(byte[] b) {
        return b.length > 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    private ConsentimentoProvadorResponse toResponse(ConsentimentoProvador consentimento) {
        return new ConsentimentoProvadorResponse(
                consentimento.estaAtivo(versaoAtual),
                consentimento.getVersao(),
                consentimento.getAceitoEm());
    }
}
