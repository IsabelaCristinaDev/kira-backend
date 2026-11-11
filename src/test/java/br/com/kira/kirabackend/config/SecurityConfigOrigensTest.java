package br.com.kira.kirabackend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Regra pura, sem Spring: a lista de origens vem da variável KIRA_CORS_ORIGENS
class SecurityConfigOrigensTest {

    @Test
    @DisplayName("Origens válidas passam sem alteração")
    void origensValidas() {
        // Arrange
        List<String> origens = List.of("http://localhost:5173", "https://kira.exemplo.com");

        // Act
        List<String> resultado = SecurityConfig.validarOrigens(origens);

        // Assert
        assertEquals(origens, resultado);
    }

    @Test
    @DisplayName("A barra no fim é removida, porque o navegador manda a origem sem ela")
    void removeBarraFinal() {
        List<String> resultado = SecurityConfig.validarOrigens(List.of("https://kira.exemplo.com/"));

        assertEquals(List.of("https://kira.exemplo.com"), resultado);
    }

    @Test
    @DisplayName("Espaços e itens vazios da variável são ignorados")
    void ignoraEspacosEVazios() {
        List<String> resultado = SecurityConfig.validarOrigens(List.of(" https://kira.exemplo.com ", "", "  "));

        assertEquals(List.of("https://kira.exemplo.com"), resultado);
    }

    @Test
    @DisplayName("Lista vazia impede a aplicação de subir")
    void listaVaziaFalha() {
        IllegalStateException erro = assertThrows(IllegalStateException.class,
                () -> SecurityConfig.validarOrigens(List.of()));

        assertTrue(erro.getMessage().contains("KIRA_CORS_ORIGENS"));
    }

    @Test
    @DisplayName("Lista nula impede a aplicação de subir")
    void listaNulaFalha() {
        assertThrows(IllegalStateException.class, () -> SecurityConfig.validarOrigens(null));
    }

    @Test
    @DisplayName("Curinga * é recusado, porque o CORS usa credenciais")
    void curingaFalha() {
        List<String> origens = List.of("*");

        assertThrows(IllegalStateException.class, () -> SecurityConfig.validarOrigens(origens));
    }

    @Test
    @DisplayName("Origem sem http:// ou https:// é recusada")
    void semProtocoloFalha() {
        List<String> origens = List.of("kira.exemplo.com");

        assertThrows(IllegalStateException.class, () -> SecurityConfig.validarOrigens(origens));
    }
}
