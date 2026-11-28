package br.com.kira.kirabackend.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitesDeTextoTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    static Stream<Arguments> campos() {
        return Stream.of(
                Arguments.of(AvaliacaoRequest.class, "comentario", 500, "Comentário deve ter no máximo 500 caracteres"),
                Arguments.of(AgendamentoRequest.class, "observacoes", 500, "Observações devem ter no máximo 500 caracteres"),
                Arguments.of(ClienteRegistroRequest.class, "nome", 150, "Nome deve ter no máximo 150 caracteres"),
                Arguments.of(EmpresaRegistroRequest.class, "nome", 150, "Nome deve ter no máximo 150 caracteres"),
                Arguments.of(EmpresaRegistroRequest.class, "descricao", 500, "Descrição deve ter no máximo 500 caracteres"),
                Arguments.of(EmpresaPerfilRequest.class, "descricao", 500, "Descrição deve ter no máximo 500 caracteres"),
                Arguments.of(FuncionariaRequest.class, "nome", 150, "Nome deve ter no máximo 150 caracteres"),
                Arguments.of(ServicoRequest.class, "nome", 150, "Nome deve ter no máximo 150 caracteres"),
                Arguments.of(ServicoRequest.class, "descricao", 500, "Descrição deve ter no máximo 500 caracteres")
        );
    }

    @ParameterizedTest(name = "{0}.{1} aceita até {2} caracteres")
    @MethodSource("campos")
    @DisplayName("Texto no tamanho máximo é aceito")
    void aceitaNoLimite(Class<?> tipo, String campo, int limite, String mensagem) {
        assertTrue(validador.validateValue(tipo, campo, "a".repeat(limite)).isEmpty());
    }

    @ParameterizedTest(name = "{0}.{1} recusa {2} + 1 caracteres")
    @MethodSource("campos")
    @DisplayName("Texto acima do máximo é recusado com mensagem clara")
    void recusaAcimaDoLimite(Class<?> tipo, String campo, int limite, String mensagem) {
        var violacoes = validador.validateValue(tipo, campo, "a".repeat(limite + 1));

        assertEquals(1, violacoes.size());
        assertEquals(mensagem, violacoes.iterator().next().getMessage());
    }
}
