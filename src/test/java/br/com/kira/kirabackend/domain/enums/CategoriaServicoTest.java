package br.com.kira.kirabackend.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoriaServicoTest {

    @ParameterizedTest(name = "\"{0}\" é {1}")
    @CsvSource({
            "Corte feminino, CABELO",
            "Escova progressiva, CABELO",
            "Mechas e luzes, CABELO",
            "Barba na navalha, BARBA",
            "Corte de barba, BARBA",
            "Manicure, UNHAS",
            "Alongamento em gel, UNHAS",
            "Alongamento de cílios, CILIOS",
            "Lash lifting, CILIOS",
            "Design de sobrancelha com henna, SOBRANCELHAS",
            "Maquiagem social, MAQUIAGEM",
            "Limpeza de pele, ESTETICA",
            "ESTÉTICA FACIAL, ESTETICA",
            "Drenagem linfática, MASSAGEM",
            "Massagem relaxante, MASSAGEM",
            "Tatuagem, OUTRO"
    })
    @DisplayName("Deduz a categoria pelo nome, sem ligar para acento e maiúsculas")
    void peloNome(String nome, CategoriaServico esperada) {
        assertEquals(esperada, CategoriaServico.peloNome(nome));
    }

    @Test
    @DisplayName("Nome nulo é OUTRO")
    void nomeNulo() {
        assertEquals(CategoriaServico.OUTRO, CategoriaServico.peloNome(null));
    }

    @Test
    @DisplayName("A categoria escolhida vale mais que o nome")
    void escolhidaVence() {
        assertEquals(CategoriaServico.ESTETICA,
                CategoriaServico.escolhidaOuPeloNome(CategoriaServico.ESTETICA, "Corte"));
        assertEquals(CategoriaServico.CABELO, CategoriaServico.escolhidaOuPeloNome(null, "Corte"));
    }
}
