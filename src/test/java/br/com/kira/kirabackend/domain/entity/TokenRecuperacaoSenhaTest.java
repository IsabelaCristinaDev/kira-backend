package br.com.kira.kirabackend.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Os valores esperados foram calculados fora do Java (sha256sum), e não com o hashDoToken:
 * se o método calculasse errado, um teste que usa ele mesmo como referência passaria igual.
 */
class TokenRecuperacaoSenhaTest {

    @Test
    @DisplayName("O hash de \"abc\" é o valor de referência do SHA-256 publicado pelo NIST")
    void hashDeAbcBateComVetorDoNist() {
        // Arrange
        String esperado = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

        // Act
        String hash = TokenRecuperacaoSenha.hashDoToken("abc");

        // Assert
        assertEquals(esperado, hash);
    }

    @Test
    @DisplayName("O hash do texto vazio é o valor de referência do SHA-256 para entrada vazia")
    void hashDoTextoVazio() {
        String hash = TokenRecuperacaoSenha.hashDoToken("");

        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash);
    }

    @Test
    @DisplayName("Acentos são lidos em UTF-8: o hash não muda conforme a configuração da máquina")
    void acentosEmUtf8() {
        String hash = TokenRecuperacaoSenha.hashDoToken("ação");

        // SHA-256 dos bytes UTF-8 de "ação". Em Latin-1 daria 5021bb98...
        assertEquals("0664077f33cc3ebbaa4bbdacac0eb70e740983080f01dce29929e73b7785a7ad", hash);
    }

    @Test
    @DisplayName("Tokens diferentes geram hashes diferentes, e o mesmo token sempre gera o mesmo hash")
    void mesmoTokenMesmoHash() {
        String token = "Zm9vYmFyLXRva2VuLWRlLTMyLWJ5dGVzLWVtLWJhc2U2NA";

        String primeiro = TokenRecuperacaoSenha.hashDoToken(token);
        String segundo = TokenRecuperacaoSenha.hashDoToken(token);
        String outro = TokenRecuperacaoSenha.hashDoToken(token + "x");

        // Determinístico: é o que permite achar o registro pela busca direta no banco
        assertEquals(primeiro, segundo);
        assertNotEquals(primeiro, outro);
    }
}
