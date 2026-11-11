package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Confere e guarda as imagens enviadas pelas contas. É usada pela foto de perfil, pelo portfólio e
 * pela compatibilidade do Provador.
 */
@Component
@RequiredArgsConstructor
public class ImagensEnviadas {

    public static final String PREFIXO_URL = "/api/arquivos/";

    private static final Logger log = LoggerFactory.getLogger(ImagensEnviadas.class);
    private static final byte[] INICIO_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] INICIO_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private final ArmazenamentoArquivos armazenamento;

    /** Guarda a imagem e devolve o endereço dela. */
    public String guardar(MultipartFile arquivo) {
        byte[] conteudo = lerConteudo(arquivo);
        return PREFIXO_URL + armazenamento.salvar(conteudo, extensaoDaImagem(conteudo));
    }

    /** Confere a imagem enviada e devolve o conteúdo, sem guardar nada. */
    public byte[] lerImagem(MultipartFile arquivo) {
        byte[] conteudo = lerConteudo(arquivo);
        extensaoDaImagem(conteudo);
        return conteudo;
    }

    /** Conteúdo de uma imagem já guardada aqui. Vazio se o endereço for de fora ou o arquivo não puder ser lido. */
    public Optional<byte[]> lerGuardada(String url) {
        if (url == null || !url.startsWith(PREFIXO_URL)) {
            return Optional.empty();
        }
        String nome = url.substring(PREFIXO_URL.length());
        return armazenamento.abrir(nome).flatMap(arquivo -> {
            try (InputStream conteudo = arquivo.getInputStream()) {
                return Optional.of(conteudo.readAllBytes());
            } catch (IOException e) {
                log.warn("Não foi possível ler o arquivo {}", nome, e);
                return Optional.empty();
            }
        });
    }

    /** Usada depois que a troca já valeu: sobrar um arquivo na pasta não deve virar erro para a pessoa. */
    public void apagarSemFalhar(String url) {
        // Fotos antigas podem ter um endereço de fora: só apaga o que foi guardado aqui
        if (url == null || !url.startsWith(PREFIXO_URL)) {
            return;
        }
        String nome = url.substring(PREFIXO_URL.length());
        try {
            armazenamento.apagar(nome);
        } catch (RuntimeException e) {
            log.warn("Não foi possível apagar o arquivo {}", nome, e);
        }
    }

    private static byte[] lerConteudo(MultipartFile arquivo) {
        if (arquivo.isEmpty()) {
            throw new RegraDeNegocioException("Envie uma foto");
        }
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo enviado", e);
        }
    }

    // Olha o conteúdo do arquivo: o nome e o tipo informados no envio podem ser qualquer coisa
    private static String extensaoDaImagem(byte[] conteudo) {
        if (comecaCom(conteudo, INICIO_JPEG)) {
            return "jpg";
        }
        if (comecaCom(conteudo, INICIO_PNG)) {
            return "png";
        }
        if (ehWebp(conteudo)) {
            return "webp";
        }
        throw new RegraDeNegocioException("A foto precisa ser JPG, PNG ou WEBP");
    }

    private static boolean comecaCom(byte[] conteudo, byte[] inicio) {
        return conteudo.length >= inicio.length
                && Arrays.equals(Arrays.copyOf(conteudo, inicio.length), inicio);
    }

    private static boolean ehWebp(byte[] conteudo) {
        return conteudo.length >= 12
                && "RIFF".equals(new String(conteudo, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(conteudo, 8, 4, StandardCharsets.US_ASCII));
    }
}
