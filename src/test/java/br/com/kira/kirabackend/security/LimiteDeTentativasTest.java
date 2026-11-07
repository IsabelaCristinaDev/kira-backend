package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.config.LimiteTentativasPropriedades;
import br.com.kira.kirabackend.config.LimiteTentativasPropriedades.Limite;
import br.com.kira.kirabackend.config.LimiteTentativasPropriedades.Login;
import br.com.kira.kirabackend.config.LimiteTentativasPropriedades.PorEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Sem Spring: o relógio é falso, então "15 minutos" passam num instante
class LimiteDeTentativasTest {

    private static final String IP = "200.1.1.1";
    private static final String OUTRO_IP = "200.2.2.2";
    private static final String EMAIL = "ana@teste.com";

    // Mesmos números do application.yml
    static final LimiteTentativasPropriedades PROPRIEDADES = new LimiteTentativasPropriedades(new Login(
            new Limite(5, Duration.ofMinutes(15)),
            new Limite(20, Duration.ofMinutes(15)),
            new PorEmail(5, Duration.ofSeconds(1), Duration.ofSeconds(8), Duration.ofMinutes(15),
                    10, Duration.ofHours(1))));

    private final AtomicLong agoraNanos = new AtomicLong();
    private LimiteDeTentativas limite;

    @BeforeEach
    void setUp() {
        limite = new LimiteDeTentativas(PROPRIEDADES, agoraNanos::get);
    }

    private void falhar(String ip, String email, int vezes) {
        for (int i = 0; i < vezes; i++) {
            limite.registrarFalha(ip, email);
        }
    }

    private void avancar(Duration tempo) {
        agoraNanos.addAndGet(tempo.toNanos());
    }

    @Test
    @DisplayName("Com 4 falhas do mesmo IP na mesma conta, ainda não há bloqueio")
    void quatroFalhasSemBloqueio() {
        falhar(IP, EMAIL, 4);

        assertTrue(limite.bloqueio(IP, EMAIL).isEmpty());
    }

    @Test
    @DisplayName("Com 5 falhas do mesmo IP na mesma conta, a 6ª tentativa é bloqueada até a janela acabar")
    void cincoFalhasBloqueiam() {
        falhar(IP, EMAIL, 5);

        Optional<Duration> espera = limite.bloqueio(IP, EMAIL);

        assertTrue(espera.isPresent());
        assertEquals(Duration.ofMinutes(15), espera.get());
    }

    @Test
    @DisplayName("O bloqueio do par IP + conta não atinge a mesma conta vinda de outro IP")
    void outroIpNaoBloqueado() {
        falhar(IP, EMAIL, 5);

        assertTrue(limite.bloqueio(OUTRO_IP, EMAIL).isEmpty());
    }

    @Test
    @DisplayName("Depois da janela de 15 minutos, o par IP + conta volta a poder tentar")
    void janelaAcabaLibera() {
        falhar(IP, EMAIL, 5);

        avancar(Duration.ofMinutes(15));

        assertTrue(limite.bloqueio(IP, EMAIL).isEmpty());
    }

    @Test
    @DisplayName("Login com sucesso zera as falhas do par IP + conta")
    void sucessoZera() {
        falhar(IP, EMAIL, 4);

        limite.registrarSucesso(IP, EMAIL);
        falhar(IP, EMAIL, 4);

        assertTrue(limite.bloqueio(IP, EMAIL).isEmpty());
    }

    @Test
    @DisplayName("20 falhas do mesmo IP, em contas diferentes, bloqueiam o IP inteiro")
    void vinteFalhasBloqueiamOIp() {
        for (int i = 0; i < 20; i++) {
            limite.registrarFalha(IP, "conta" + i + "@teste.com");
        }

        assertTrue(limite.bloqueio(IP, "outra-conta@teste.com").isPresent());
        assertTrue(limite.bloqueio(OUTRO_IP, "outra-conta@teste.com").isEmpty());
    }

    @Test
    @DisplayName("O e-mail é comparado sem diferença de maiúsculas nem espaços")
    void emailNormalizado() {
        falhar(IP, "  Ana@Teste.COM ", 5);

        assertTrue(limite.bloqueio(IP, EMAIL).isPresent());
    }

    @Test
    @DisplayName("Até 4 falhas na conta, não há atraso nem verificação extra")
    void semAtrasoAteQuatroFalhas() {
        falhar(IP, EMAIL, 4);

        assertEquals(Duration.ZERO, limite.atraso(EMAIL));
        assertFalse(limite.exigeVerificacao(EMAIL));
    }

    @Test
    @DisplayName("A partir de 5 falhas na conta (de qualquer IP), o atraso dobra: 1 s, 2 s, 4 s e no máximo 8 s")
    void atrasoProgressivo() {
        // Cada falha vem de um IP diferente: a conta não é bloqueada, só atrasa
        String[] ips = {"1.1.1.1", "2.2.2.2", "3.3.3.3", "4.4.4.4", "5.5.5.5", "6.6.6.6", "7.7.7.7", "8.8.8.8", "9.9.9.9"};
        for (int i = 0; i < 5; i++) {
            limite.registrarFalha(ips[i], EMAIL);
        }
        assertEquals(Duration.ofSeconds(1), limite.atraso(EMAIL));

        limite.registrarFalha(ips[5], EMAIL);
        assertEquals(Duration.ofSeconds(2), limite.atraso(EMAIL));

        limite.registrarFalha(ips[6], EMAIL);
        assertEquals(Duration.ofSeconds(4), limite.atraso(EMAIL));

        limite.registrarFalha(ips[7], EMAIL);
        assertEquals(Duration.ofSeconds(8), limite.atraso(EMAIL));

        limite.registrarFalha(ips[8], EMAIL);
        assertEquals(Duration.ofSeconds(8), limite.atraso(EMAIL));
        assertTrue(limite.exigeVerificacao(EMAIL));
    }

    @Test
    @DisplayName("15 minutos sem falhas na conta zeram o atraso")
    void atrasoZeraComOTempo() {
        falhar(IP, EMAIL, 5);

        avancar(Duration.ofMinutes(15));

        assertEquals(Duration.ZERO, limite.atraso(EMAIL));
        assertFalse(limite.exigeVerificacao(EMAIL));
    }

    @Test
    @DisplayName("O aviso por e-mail sai na 10ª falha da conta, e no máximo uma vez por hora")
    void avisoUmaVezPorHora() {
        falhar(OUTRO_IP, EMAIL, 9);
        assertFalse(limite.deveAvisar(EMAIL));

        limite.registrarFalha(OUTRO_IP, EMAIL);
        assertTrue(limite.deveAvisar(EMAIL));
        assertFalse(limite.deveAvisar(EMAIL));

        // A conta continua sendo atacada; passada 1 hora, pode avisar de novo
        avancar(Duration.ofMinutes(61));
        falhar(IP, EMAIL, 10);
        assertTrue(limite.deveAvisar(EMAIL));
    }

    @Test
    @DisplayName("O e-mail no log aparece mascarado")
    void mascaraEmail() {
        assertEquals("is***@gmail.com", LimiteDeTentativas.mascarar("isabela@gmail.com"));
        assertEquals("a***@teste.com", LimiteDeTentativas.mascarar("ab@teste.com"));
        assertEquals("***", LimiteDeTentativas.mascarar("sem-arroba"));
        assertEquals("***", LimiteDeTentativas.mascarar(null));
    }
}
