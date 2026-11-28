package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.RedefinicaoSenhaRequest;
import br.com.kira.kirabackend.dto.request.TrocaSenhaRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenhaCabeNoBcryptTest {

    private static final String MENSAGEM = "Senha deve ter no máximo 72 caracteres (letras com acento contam como 2)";

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    /** Os quatro campos que criam uma senha nova. */
    static Stream<Arguments> camposDeSenhaNova() {
        return Stream.of(
                Arguments.of(ClienteRegistroRequest.class, "senha"),
                Arguments.of(EmpresaRegistroRequest.class, "senha"),
                Arguments.of(TrocaSenhaRequest.class, "novaSenha"),
                Arguments.of(RedefinicaoSenhaRequest.class, "novaSenha"));
    }

    private Set<? extends ConstraintViolation<?>> validar(Class<?> classe, String campo, String senha) {
        return validador.validateValue(classe, campo, senha);
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("camposDeSenhaNova")
    @DisplayName("Senha com exatamente 72 bytes é aceita")
    void aceitaNoLimite(Class<?> classe, String campo) {
        assertTrue(validar(classe, campo, "a".repeat(72)).isEmpty());
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("camposDeSenhaNova")
    @DisplayName("Senha com 73 bytes é recusada com mensagem clara")
    void recusaAcimaDoLimite(Class<?> classe, String campo) {
        var violacoes = validar(classe, campo, "a".repeat(73));

        assertEquals(1, violacoes.size());
        assertEquals(MENSAGEM, violacoes.iterator().next().getMessage());
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("camposDeSenhaNova")
    @DisplayName("Letra com acento conta como 2 bytes: 37 letras acentuadas passam do limite")
    void contaBytesDeAcentos(Class<?> classe, String campo) {
        assertTrue(validar(classe, campo, "é".repeat(36)).isEmpty());
        assertEquals(1, validar(classe, campo, "é".repeat(37)).size());
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("camposDeSenhaNova")
    @DisplayName("Senha ausente fica a cargo do @NotBlank, sem mensagem repetida")
    void ignoraSenhaAusente(Class<?> classe, String campo) {
        var violacoes = validar(classe, campo, null);

        assertTrue(violacoes.stream().noneMatch(v -> MENSAGEM.equals(v.getMessage())));
    }
}
