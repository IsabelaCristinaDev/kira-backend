package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Imagem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VerificadorDeIdentidadeTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

    @Test
    @DisplayName("Imagens com o mesmo conteúdo em arrays diferentes são iguais")
    void mesmoConteudoEhIgual() {
        // Arrange
        Imagem uma = new Imagem(JPEG.clone(), MediaType.IMAGE_JPEG);
        Imagem outra = new Imagem(JPEG.clone(), MediaType.IMAGE_JPEG);

        // Act + Assert
        assertEquals(uma, outra);
        assertEquals(uma.hashCode(), outra.hashCode());
    }

    @Test
    @DisplayName("Imagens com conteúdo ou tipo diferente não são iguais")
    void conteudoOuTipoDiferenteNaoEhIgual() {
        // Arrange
        Imagem base = new Imagem(JPEG.clone(), MediaType.IMAGE_JPEG);
        byte[] alterado = JPEG.clone();
        alterado[5] = 9;

        // Act + Assert
        assertNotEquals(base, new Imagem(alterado, MediaType.IMAGE_JPEG));
        assertNotEquals(base, new Imagem(JPEG.clone(), MediaType.IMAGE_PNG));
        assertNotEquals(base, null);
    }

    @Test
    @DisplayName("O toString mostra só o tamanho e o tipo, nunca os bytes da foto")
    void toStringNaoMostraOsBytes() {
        // Arrange
        Imagem imagem = new Imagem(JPEG.clone(), MediaType.IMAGE_JPEG);

        // Act
        String texto = imagem.toString();

        // Assert
        assertEquals("Imagem[6 bytes, image/jpeg]", texto);
        assertFalse(texto.contains("[B@"), "Não deve mostrar o array");
    }
}
