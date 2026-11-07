package br.com.kira.kirabackend.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnderecoRequestTest {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private static EnderecoRequest comCep(String cep) {
        return new EnderecoRequest(cep, "Rua A", "1", null, "Centro", "Goiania", "GO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"74115-060", "74115060", "74.115-060", " 74115-060 "})
    @DisplayName("CEP com hífen, ponto ou espaços é gravado só com os 8 dígitos")
    void normalizaCep(String cep) {
        // Arrange
        EnderecoRequest request = comCep(cep);

        // Act + Assert
        assertTrue(validador.validate(request).isEmpty());
        assertEquals("74115060", request.paraEndereco().getCep());
    }

    @ParameterizedTest
    @ValueSource(strings = {"7411-506", "741150600", "74115-06a", "CEP 74115060", "1234567"})
    @DisplayName("CEP que não tem exatamente 8 dígitos é recusado com mensagem clara")
    void recusaCepInvalido(String cep) {
        // Act
        var violacoes = validador.validate(comCep(cep));

        // Assert
        assertEquals(1, violacoes.size());
        var violacao = violacoes.iterator().next();
        assertEquals("cep", violacao.getPropertyPath().toString());
        assertEquals("CEP deve ter 8 dígitos", violacao.getMessage());
    }

    @Test
    @DisplayName("CEP ausente ou em branco continua opcional e é gravado vazio")
    void cepOpcional() {
        assertTrue(validador.validate(comCep(null)).isEmpty());
        assertTrue(validador.validate(comCep("")).isEmpty());
        assertNull(comCep(null).paraEndereco().getCep());
        assertNull(comCep("  ").paraEndereco().getCep());
    }

    @Test
    @DisplayName("O CEP do endereço é validado dentro do cadastro e da edição de perfil")
    void validaEnderecoAninhado() {
        // Arrange
        EnderecoRequest endereco = comCep("7411-506");
        var cadastro = new ClienteRegistroRequest("Ana", "ana@example.com", "Senha1234", "62999990000",
                "52998224725", java.time.LocalDate.of(1995, 1, 1),
                br.com.kira.kirabackend.domain.enums.Genero.FEMININO, endereco, null, null);

        // Act
        var violacoes = validador.validate(cadastro);

        // Assert
        assertEquals(1, violacoes.size());
        assertEquals("endereco.cep", violacoes.iterator().next().getPropertyPath().toString());
    }
}
