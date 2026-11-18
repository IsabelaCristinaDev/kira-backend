package br.com.kira.kirabackend.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuncionariaRequestTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private static FuncionariaRequest comComissao(BigDecimal percentual) {
        return new FuncionariaRequest("Bia", "Cílios", percentual);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.01", "30", "37.5", "99.99", "100", "100.00"})
    @DisplayName("Comissão de 0% a 100%, com até 2 casas decimais, é aceita")
    void aceitaComissaoDentroDoLimite(String percentual) {
        // Arrange
        FuncionariaRequest request = comComissao(new BigDecimal(percentual));

        // Act
        var violacoes = validador.validate(request);

        // Assert
        assertTrue(violacoes.isEmpty());
    }

    @Test
    @DisplayName("Funcionária sem comissão definida continua permitida")
    void aceitaSemComissao() {
        assertTrue(validador.validate(comComissao(null)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "-10"})
    @DisplayName("Comissão negativa é recusada com mensagem clara")
    void recusaComissaoNegativa(String percentual) {
        // Act
        var violacoes = validador.validate(comComissao(new BigDecimal(percentual)));

        // Assert
        assertEquals(1, violacoes.size());
        var violacao = violacoes.iterator().next();
        assertEquals("comissaoPercentual", violacao.getPropertyPath().toString());
        assertEquals("Comissão deve ser de no mínimo 0%", violacao.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.01", "150"})
    @DisplayName("Comissão acima de 100% é recusada com mensagem clara")
    void recusaComissaoAcimaDeCem(String percentual) {
        // Act
        var violacoes = validador.validate(comComissao(new BigDecimal(percentual)));

        // Assert
        assertEquals(1, violacoes.size());
        var violacao = violacoes.iterator().next();
        assertEquals("comissaoPercentual", violacao.getPropertyPath().toString());
        assertEquals("Comissão deve ser de no máximo 100%", violacao.getMessage());
    }

    @Test
    @DisplayName("Comissão com mais de 2 casas decimais é recusada em vez de ser arredondada pelo banco")
    void recusaMaisDeDuasCasas() {
        // Act
        var violacoes = validador.validate(comComissao(new BigDecimal("30.555")));

        // Assert
        assertEquals(1, violacoes.size());
        assertEquals("Comissão deve ter no máximo 2 casas decimais",
                violacoes.iterator().next().getMessage());
    }
}
