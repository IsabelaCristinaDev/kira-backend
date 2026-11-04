package br.com.kira.kirabackend.service;

import org.springframework.core.io.Resource;

import java.util.Optional;

/**
 * Onde os arquivos enviados ficam guardados. Hoje é uma pasta local; para usar outro serviço, como o Amazon S3,
 * basta uma nova implementação desta interface.
 */
public interface ArmazenamentoArquivos {

    /** Guarda o conteúdo com um nome aleatório e devolve esse nome. */
    String salvar(byte[] conteudo, String extensao);

    Optional<Resource> abrir(String nome);

    void apagar(String nome);
}
