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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
    @DisplayName("A pasta é criada já na subida, antes do primeiro envio")
    void criaPastaNaSubida() {
        // Arrange + Act: o setUp já criou o armazenamento
        // Assert
        assertTrue(Files.isDirectory(pasta));
    }

    @Test
    @DisplayName("Se a pasta não pode ser criada, a aplicação não sobe e a mensagem cita a variável")
    void pastaImpossivelNaoSobe() throws IOException {
        // Arrange: um arquivo comum ocupa o lugar de onde a pasta ficaria
        Path ocupado = raiz.resolve("ocupado");
        Files.write(ocupado, new byte[]{1});
        String pastaDentroDoArquivo = ocupado.resolve("uploads").toString();

        // Act
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> new ArmazenamentoLocal(pastaDentroDoArquivo));

        // Assert
        assertTrue(erro.getMessage().contains("KIRA_ARQUIVOS_PASTA"));
    }

    @Test
    @DisplayName("Se a pasta existe mas não aceita gravação, a aplicação não sobe")
    void pastaSemPermissaoNaoSobe() throws IOException {
        // Arrange: pasta só de leitura, como /app quando o container não roda como root
        Path somenteLeitura = Files.createDirectory(raiz.resolve("somente-leitura"));
        assumeTrue(somenteLeitura.toFile().setWritable(false), "o sistema não permite tirar a permissão");
        try {
            // Como root a gravação é sempre liberada, então o teste não teria o que provar
            assumeFalse(Files.isWritable(somenteLeitura), "rodando como root");

            // Act + Assert
            String caminho = somenteLeitura.toString();
            IllegalStateException erro = assertThrows(IllegalStateException.class,
                    () -> new ArmazenamentoLocal(caminho));
            assertTrue(erro.getMessage().contains(caminho));
        } finally {
            // Devolve a permissão para o JUnit conseguir apagar a pasta temporária
            somenteLeitura.toFile().setWritable(true);
        }
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
