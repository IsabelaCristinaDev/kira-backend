package br.com.kira.kirabackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmazenamentoLocalTest {

    @TempDir
    Path raiz;

    private Path pasta;
    private ArmazenamentoLocal armazenamento;

    @BeforeEach
    void setUp() {
        pasta = raiz.resolve("uploads");
        armazenamento = new ArmazenamentoLocal(pasta.toString());
    }

    @Test
    @DisplayName("Salva com nome aleatório, cria a pasta e abre o mesmo conteúdo")
    void salvaEAbre() throws IOException {
        byte[] conteudo = {1, 2, 3};

        String nome = armazenamento.salvar(conteudo, "png");

        assertTrue(nome.endsWith(".png"));
        assertArrayEquals(conteudo, armazenamento.abrir(nome).orElseThrow().getContentAsByteArray());
    }

    @Test
    @DisplayName("Dois envios iguais geram nomes diferentes")
    void nomesDiferentes() {
        assertNotEquals(armazenamento.salvar(new byte[]{1}, "png"), armazenamento.salvar(new byte[]{1}, "png"));
    }

    @Test
    @DisplayName("Apagar remove o arquivo, e apagar de novo não dá erro")
    void apaga() {
        String nome = armazenamento.salvar(new byte[]{1}, "png");

        armazenamento.apagar(nome);
        armazenamento.apagar(nome);

        assertTrue(armazenamento.abrir(nome).isEmpty());
    }

    @Test
    @DisplayName("Arquivo que não existe não abre")
    void arquivoInexistente() {
        assertTrue(armazenamento.abrir("00000000-0000-0000-0000-000000000000.png").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../segredo.png", "..", "a/b.png", "segredo.png", ""})
    @DisplayName("Nome fora do padrão não abre nem apaga nada, mesmo que o arquivo exista")
    void nomeForaDoPadrao(String nome) throws IOException {
        Path fora = raiz.resolve("segredo.png");
        Files.write(fora, new byte[]{1});
        Files.createDirectories(pasta);
        Files.write(pasta.resolve("segredo.png"), new byte[]{1});

        assertTrue(armazenamento.abrir(nome).isEmpty());
        armazenamento.apagar(nome);

        assertTrue(Files.exists(fora));
        assertTrue(Files.exists(pasta.resolve("segredo.png")));
        assertEquals(1, Files.readAllBytes(fora).length);
    }
}
