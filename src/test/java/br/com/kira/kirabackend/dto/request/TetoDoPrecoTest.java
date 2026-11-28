package br.com.kira.kirabackend.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Teto do preço do serviço")
class TetoDoPrecoTest {

    private static jakarta.validation.ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void criarValidator() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void fecharValidator() {
        fabrica.close();
    }

    private Set<ConstraintViolation<ServicoRequest>> validar(String preco) {
        return validator.validateValue(ServicoRequest.class, "preco", new BigDecimal(preco));
    }

    @ParameterizedTest(name = "R$ {0} é aceito")
    @ValueSource(strings = {"0.01", "450.00", "9999.99", "10000.00"})
    @DisplayName("Preço até o teto, com até 2 casas, é aceito")
    void precoDentroDoTeto(String preco) {
        assertTrue(validar(preco).isEmpty());
    }

    @ParameterizedTest(name = "R$ {0} é recusado")
    @CsvSource({
            "10000.01,   'Preço deve ser de no máximo R$ 10.000,00'",
            "9999999999, 'Preço deve ser de no máximo R$ 10.000,00'",
            "49.999,     'Preço deve ter no máximo 2 casas decimais'"
    })
    @DisplayName("Preço acima do teto ou com mais de 2 casas é recusado")
    void precoForaDoTeto(String preco, String mensagem) {
        Set<ConstraintViolation<ServicoRequest>> violacoes = validar(preco);

        assertTrue(violacoes.stream().anyMatch(v -> v.getMessage().equals(mensagem)),
                () -> "mensagens: " + violacoes.stream().map(ConstraintViolation::getMessage).toList());
    }
}
