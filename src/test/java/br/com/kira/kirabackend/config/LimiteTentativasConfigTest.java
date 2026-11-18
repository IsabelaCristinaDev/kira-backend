package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.security.ProtecaoLogin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A pausa de verdade do login (a que atrasa a resposta depois de várias senhas erradas). Os outros
 * testes usam uma pausa que não espera; aqui é conferido o Thread.sleep real e o que acontece se a
 * thread for interrompida no meio, por exemplo quando a API está sendo desligada.
 */
class LimiteTentativasConfigTest {

    private final ProtecaoLogin.Pausa pausa = new LimiteTentativasConfig().pausaDoLogin();

    // Garante que a marca de interrupção não vaze para o próximo teste que rodar nesta thread
    @AfterEach
    void limparInterrupcao() {
        Thread.interrupted();
    }

    @Test
    @DisplayName("A pausa espera pelo menos o tempo pedido")
    void esperaOTempoPedido() {
        // Arrange
        Duration tempo = Duration.ofMillis(50);
        long antes = System.nanoTime();

        // Act
        pausa.pausar(tempo);

        // Assert
        long esperado = System.nanoTime() - antes;
        assertTrue(esperado >= tempo.toNanos(), "esperou só " + Duration.ofNanos(esperado).toMillis() + " ms");
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    @DisplayName("Thread interrompida: a pausa volta na hora e mantém a marca de interrupção")
    void interrompidaVoltaNaHoraEMantemAMarca() {
        // Arrange: com a marca de interrupção, o Thread.sleep lança InterruptedException assim que começa
        Thread.currentThread().interrupt();

        // Act: pede 10 s, mas tem que voltar bem antes de 1 s
        assertTimeout(Duration.ofSeconds(1), () -> pausa.pausar(Duration.ofSeconds(10)));

        // Assert: a exceção não é escondida; quem chamou ainda consegue ver que a thread foi interrompida
        assertTrue(Thread.currentThread().isInterrupted());
    }
}
