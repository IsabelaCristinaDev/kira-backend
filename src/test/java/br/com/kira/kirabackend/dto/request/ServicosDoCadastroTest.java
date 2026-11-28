package br.com.kira.kirabackend.dto.request;

import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Serviços enviados no cadastro do estúdio")
class ServicosDoCadastroTest {

    private static ValidatorFactory fabrica;
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

    private static EmpresaRegistroRequest cadastroCom(List<ServicoRegistroRequest> servicos) {
        return new EmpresaRegistroRequest("Estúdio Kira", "estudio@teste.com", "senha12345", "11988888888",
                "12.345.678/0001-90", null, null, null, null, null, null,
                List.of("Cabelo"), servicos, TipoEstabelecimento.ESTABELECIMENTO);
    }

    // Só as violações da lista de serviços: o resto do cadastro tem testes próprios
    private Set<String> violacoesDosServicos(List<ServicoRegistroRequest> servicos) {
        return validator.validate(cadastroCom(servicos)).stream()
                .filter(v -> v.getPropertyPath().toString().startsWith("servicos"))
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Serviço como a tela manda é aceito")
    void servicoValido() {
        assertTrue(violacoesDosServicos(List.of(new ServicoRegistroRequest("Corte", "R$ 50,00", "45 min", null)))
                .isEmpty());
    }

    @Test
    @DisplayName("Lista vazia ou ausente continua aceita")
    void listaVaziaOuNula() {
        assertTrue(violacoesDosServicos(List.of()).isEmpty());
        assertTrue(violacoesDosServicos(null).isEmpty());
    }

    @ParameterizedTest(name = "{3}")
    @CsvSource({
            "'',     'R$ 50', '45 min', 'servicos[0].nome: Nome do serviço é obrigatório'",
            "'Corte', '',     '45 min', 'servicos[0].preco: Preço do serviço é obrigatório'",
            "'Corte', 'R$ 50', '',      'servicos[0].duracao: Duração do serviço é obrigatória'"
    })
    @DisplayName("Cada serviço precisa de nome, preço e duração")
    void camposObrigatorios(String nome, String preco, String duracao, String esperada) {
        assertEquals(Set.of(esperada), violacoesDosServicos(List.of(new ServicoRegistroRequest(nome, preco, duracao, null))));
    }

    @Test
    @DisplayName("Nome do serviço até 150 caracteres; preço e duração até 50")
    void tamanhos() {
        assertTrue(violacoesDosServicos(List.of(
                new ServicoRegistroRequest("a".repeat(150), "1".repeat(50), "1".repeat(50), null))).isEmpty());

        assertEquals(Set.of(
                        "servicos[0].nome: Nome deve ter no máximo 150 caracteres",
                        "servicos[0].preco: Preço deve ter no máximo 50 caracteres",
                        "servicos[0].duracao: Duração deve ter no máximo 50 caracteres"),
                violacoesDosServicos(List.of(
                        new ServicoRegistroRequest("a".repeat(151), "1".repeat(51), "1".repeat(51), null))));
    }

    @Test
    @DisplayName("No máximo 50 serviços no cadastro")
    void quantidade() {
        ServicoRegistroRequest servico = new ServicoRegistroRequest("Corte", "R$ 50", "45 min", null);

        assertTrue(violacoesDosServicos(Collections.nCopies(50, servico)).isEmpty());
        assertEquals(Set.of("servicos: Cadastre no máximo 50 serviços"),
                violacoesDosServicos(Collections.nCopies(51, servico)));
    }
}
