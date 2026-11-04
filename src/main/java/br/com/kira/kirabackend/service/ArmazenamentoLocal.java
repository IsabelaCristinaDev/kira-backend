package br.com.kira.kirabackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ArmazenamentoLocal implements ArmazenamentoArquivos {

    // Só aceita os nomes gerados aqui: um nome vindo da URL nunca sai da pasta
    private static final Pattern NOME_VALIDO = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.[a-z0-9]{1,5}");

    private final Path pasta;

    public ArmazenamentoLocal(@Value("${kira.arquivos.pasta}") String pasta) {
        this.pasta = Path.of(pasta).toAbsolutePath().normalize();
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
        if (!NOME_VALIDO.matcher(nome).matches()) {
            return Optional.empty();
        }
        Path arquivo = pasta.resolve(nome);
        return Files.isRegularFile(arquivo) ? Optional.of(new PathResource(arquivo)) : Optional.empty();
    }

    @Override
    public void apagar(String nome) {
        if (!NOME_VALIDO.matcher(nome).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(pasta.resolve(nome));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível apagar o arquivo", e);
        }
    }
}
