package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.repository.ServicoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreenchimentoCategoriaServicosTest {

    @Mock
    private ServicoRepository servicoRepository;

    @InjectMocks
    private PreenchimentoCategoriaServicos preenchimento;

    private static Servico servico(String nome) {
        Servico servico = new Servico();
        servico.setNome(nome);
        return servico;
    }

    @Test
    @DisplayName("Ao subir a aplicação, os serviços sem categoria recebem a deduzida pelo nome e são salvos")
    void preencheCategoriaPeloNome() {
        Servico cilios = servico("Alongamento de cílios");
        Servico barba = servico("Corte de barba");
        Servico cabelo = servico("Hidratação capilar");
        Servico desconhecido = servico("Tatuagem");
        Servico semNome = servico(null);
        List<Servico> semCategoria = List.of(cilios, barba, cabelo, desconhecido, semNome);
        when(servicoRepository.findByCategoriaIsNull()).thenReturn(semCategoria);

        preenchimento.run(new DefaultApplicationArguments());

        // A ordem das categorias resolve o empate: "alongamento de cílios" é CILIOS, não UNHAS
        assertEquals(CategoriaServico.CILIOS, cilios.getCategoria());
        assertEquals(CategoriaServico.BARBA, barba.getCategoria());
        assertEquals(CategoriaServico.CABELO, cabelo.getCategoria());
        assertEquals(CategoriaServico.OUTRO, desconhecido.getCategoria());
        assertEquals(CategoriaServico.OUTRO, semNome.getCategoria());
        verify(servicoRepository).saveAll(semCategoria);
    }

    @Test
    @DisplayName("Sem serviços pendentes, não altera nada e a aplicação sobe normalmente")
    void semServicosPendentes() {
        when(servicoRepository.findByCategoriaIsNull()).thenReturn(List.of());

        preenchimento.run(new DefaultApplicationArguments());

        verify(servicoRepository).saveAll(List.of());
    }
}
