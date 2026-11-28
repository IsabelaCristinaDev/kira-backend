package br.com.kira.kirabackend.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;


class MascaraEmailTest {

    @Test
    @DisplayName("Um e-mail vira as duas primeiras letras, *** e o domínio")
    void mascaraUmEmail() {
        assertEquals("is***@gmail.com", MascaraEmail.mascarar("isabela@gmail.com"));
        assertEquals("a***@teste.com", MascaraEmail.mascarar("ab@teste.com"));
        assertEquals("***@teste.com", MascaraEmail.mascarar("@teste.com"));
    }

    @Test
    @DisplayName("Texto que não é e-mail, ou nulo, vira só ***")
    void semEmail() {
        assertEquals("***", MascaraEmail.mascarar("sem-arroba"));
        assertEquals("***", MascaraEmail.mascarar(null));
    }

    @Test
    @DisplayName("Mascara os e-mails no meio de um texto, como a mensagem de erro do SMTP")
    void mascaraDentroDoTexto() {
        String erroSmtp = "550 5.1.1 <ana.silva@gmail.com>: Recipient address rejected; cc bia@teste.com.br";

        assertEquals("550 5.1.1 <an***@gmail.com>: Recipient address rejected; cc bi***@teste.com.br",
                MascaraEmail.mascararNoTexto(erroSmtp));
    }

    @Test
    @DisplayName("Texto sem e-mail não muda, e nulo continua nulo")
    void textoSemEmail() {
        assertEquals("Connection timed out", MascaraEmail.mascararNoTexto("Connection timed out"));
        assertNull(MascaraEmail.mascararNoTexto(null));
    }

    @Test
    @DisplayName("Texto longo é processado rápido, com ou sem e-mail")
    void textoLongoRapido() {
        String longo = "a".repeat(50_000) + "@" + "b".repeat(50_000);

        String resultado = assertTimeoutPreemptively(Duration.ofSeconds(1), () -> MascaraEmail.mascararNoTexto(longo));

        assertEquals(longo, resultado);
    }

    @Test
    @DisplayName("Continua achando e-mails com subdomínio e terminação composta")
    void subdominios() {
        assertEquals("erro para jo***@mail.empresa.com.br agora",
                MascaraEmail.mascararNoTexto("erro para joana@mail.empresa.com.br agora"));
        assertEquals("<an***@gmail.com>", MascaraEmail.mascararNoTexto("<ana@gmail.com>"));
    }
}
