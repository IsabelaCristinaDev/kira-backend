package br.com.kira.kirabackend.service;

import org.springframework.core.io.Resource;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Onde os arquivos enviados ficam guardados. A pasta local é o padrão; com KIRA_ARQUIVOS_TIPO=s3, eles vão para o
 * MinIO (ou outro serviço compatível com S3).
 */
public interface ArmazenamentoArquivos {

    /** Guarda o conteúdo com um nome aleatório e devolve esse nome. */
    String salvar(byte[] conteudo, String extensao);

    Optional<Resource> abrir(String nome);

    void apagar(String nome);

    /** Só os nomes gerados por {@link #salvar}: UUID e extensão. Um nome vindo da URL nunca aponta para outro lugar. */
    static boolean nomeValido(String nome) {
        return nome != null && NomeDeArquivo.PADRAO.matcher(nome).matches();
    }

    final class NomeDeArquivo {
        private static final Pattern PADRAO =
                Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.[a-z0-9]{1,5}");

        private NomeDeArquivo() {
        }
    }
}
