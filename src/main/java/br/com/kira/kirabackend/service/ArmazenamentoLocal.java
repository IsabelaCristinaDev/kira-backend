package br.com.kira.kirabackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

// Padrão: vale quando KIRA_ARQUIVOS_TIPO não está definido
@Component
@ConditionalOnProperty(name = "kira.arquivos.tipo", havingValue = "local", matchIfMissing = true)
public class ArmazenamentoLocal implements ArmazenamentoArquivos {

    private final Path pasta;

    public ArmazenamentoLocal(@Value("${kira.arquivos.pasta}") String pasta) {
        this.pasta = Path.of(pasta).toAbsolutePath().normalize();
        prepararPasta();
    }

    // Confere na subida: sem isso, o erro só aparece no primeiro envio de foto, como um 500
    private void prepararPasta() {
        try {
            Files.createDirectories(pasta);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Não foi possível criar a pasta de arquivos " + pasta + ". Confira KIRA_ARQUIVOS_PASTA", e);
        }
        if (!Files.isWritable(pasta)) {
            throw new IllegalStateException(
                    "Sem permissão para gravar na pasta de arquivos " + pasta + ". Confira KIRA_ARQUIVOS_PASTA");
        }
    }

    @Override
    public String salvar(byte[] conteudo, String extensao) {
        String nome = UUID.randomUUID() + "." + extensao;
        try {
            Files.createDirectories(pasta);
            Files.write(pasta.resolve(nome), conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível guardar o arquivo", e);
        }
        return nome;
    }

    @Override
    public Optional<Resource> abrir(String nome) {
        if (!ArmazenamentoArquivos.nomeValido(nome)) {
            return Optional.empty();
        }
        Path arquivo = pasta.resolve(nome);
        return Files.isRegularFile(arquivo) ? Optional.of(new PathResource(arquivo)) : Optional.empty();
    }

    @Override
    public void apagar(String nome) {
        if (!ArmazenamentoArquivos.nomeValido(nome)) {
            return;
        }
        try {
            Files.deleteIfExists(pasta.resolve(nome));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível apagar o arquivo", e);
        }
    }
}
