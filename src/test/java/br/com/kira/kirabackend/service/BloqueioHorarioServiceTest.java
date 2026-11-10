package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.BloqueioHorario;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.dto.request.BloqueioHorarioRequest;
import br.com.kira.kirabackend.dto.response.BloqueioHorarioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.BloqueioHorarioRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloqueioHorarioServiceTest {

    private static final LocalDateTime INICIO = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Mock
    private BloqueioHorarioRepository bloqueioHorarioRepository;

    @Mock
    private FuncionariaRepository funcionariaRepository;

    @InjectMocks
    private BloqueioHorarioService service;

    private Funcionaria funcionaria;

    @BeforeEach
    void setUp() {
        funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setNome("Bia");
    }

    @Test
    @DisplayName("Cadastra o bloqueio ligado à funcionária")
    void deveCadastrarBloqueio() {
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));
        when(bloqueioHorarioRepository.save(any(BloqueioHorario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BloqueioHorarioResponse response = service.cadastrar(funcionaria.getId(),
                new BloqueioHorarioRequest(INICIO, INICIO.plusHours(2), "Consulta médica"));

        assertEquals(funcionaria.getId(), response.funcionariaId());
        assertEquals(INICIO, response.dataHoraInicio());
        assertEquals(INICIO.plusHours(2), response.dataHoraFim());
        assertEquals("Consulta médica", response.motivo());
    }

    @Test
    @DisplayName("Bloqueio com fim antes do início dá erro")
    void fimAntesDoInicioDaErro() {
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));

        UUID funcionariaId = funcionaria.getId();
        BloqueioHorarioRequest req = new BloqueioHorarioRequest(INICIO, INICIO.minusMinutes(1), null);

        RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                () -> service.cadastrar(funcionariaId, req));

        assertEquals("Data de fim deve ser após data de início", ex.getMessage());
        verify(bloqueioHorarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bloqueio para funcionária inexistente dá 404")
    void funcionariaInexistente() {
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.empty());

        UUID funcionariaId = funcionaria.getId();
        BloqueioHorarioRequest req = new BloqueioHorarioRequest(INICIO, INICIO.plusHours(1), null);

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.cadastrar(funcionariaId, req));
        verify(bloqueioHorarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Remover um bloqueio existente apaga o registro")
    void removerBloqueioExistente() {
        UUID id = UUID.randomUUID();
        BloqueioHorario bloqueio = new BloqueioHorario();
        bloqueio.setFuncionaria(funcionaria);
        when(bloqueioHorarioRepository.findById(id)).thenReturn(Optional.of(bloqueio));

        service.remover(funcionaria.getId(), id);

        verify(bloqueioHorarioRepository).deleteById(id);
    }

    @Test
    @DisplayName("Remover um bloqueio inexistente dá erro 404")
    void removerBloqueioInexistente() {
        UUID id = UUID.randomUUID();
        when(bloqueioHorarioRepository.findById(id)).thenReturn(Optional.empty());

        UUID funcionariaId = funcionaria.getId();
        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.remover(funcionariaId, id));

        assertEquals("Bloqueio não encontrado", ex.getMessage());
        verify(bloqueioHorarioRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Bloqueio de outra funcionária não é removido pela rota desta")
    void naoRemoveBloqueioDeOutraFuncionaria() {
        UUID id = UUID.randomUUID();
        Funcionaria outra = new Funcionaria();
        outra.setId(UUID.randomUUID());
        BloqueioHorario bloqueio = new BloqueioHorario();
        bloqueio.setFuncionaria(outra);
        when(bloqueioHorarioRepository.findById(id)).thenReturn(Optional.of(bloqueio));
        UUID funcionariaId = funcionaria.getId();

        assertThrows(RecursoNaoEncontradoException.class, () -> service.remover(funcionariaId, id));
        verify(bloqueioHorarioRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Lista os bloqueios da funcionária com o nome dela, o período e o motivo")
    void listaBloqueiosDaFuncionaria() {
        BloqueioHorario almoco = bloqueio(INICIO.withHour(12), INICIO.withHour(13), "Almoço");
        BloqueioHorario curso = bloqueio(INICIO.plusDays(1), INICIO.plusDays(1).withHour(18), null);
        when(bloqueioHorarioRepository.findByFuncionariaId(funcionaria.getId())).thenReturn(List.of(almoco, curso));

        List<BloqueioHorarioResponse> bloqueios = service.listarPorFuncionaria(funcionaria.getId());

        assertEquals(List.of(almoco.getId(), curso.getId()),
                bloqueios.stream().map(BloqueioHorarioResponse::id).toList());
        BloqueioHorarioResponse primeiro = bloqueios.get(0);
        assertEquals(funcionaria.getId(), primeiro.funcionariaId());
        assertEquals("Bia", primeiro.nomeFuncionaria());
        assertEquals(INICIO.withHour(12), primeiro.dataHoraInicio());
        assertEquals(INICIO.withHour(13), primeiro.dataHoraFim());
        assertEquals("Almoço", primeiro.motivo());
        assertNull(bloqueios.get(1).motivo());
    }

    @Test
    @DisplayName("Funcionária sem bloqueios recebe lista vazia")
    void listaVazia() {
        when(bloqueioHorarioRepository.findByFuncionariaId(funcionaria.getId())).thenReturn(List.of());

        assertTrue(service.listarPorFuncionaria(funcionaria.getId()).isEmpty());
    }

    private BloqueioHorario bloqueio(LocalDateTime inicio, LocalDateTime fim, String motivo) {
        BloqueioHorario bloqueio = new BloqueioHorario();
        bloqueio.setId(UUID.randomUUID());
        bloqueio.setFuncionaria(funcionaria);
        bloqueio.setDataHoraInicio(inicio);
        bloqueio.setDataHoraFim(fim);
        bloqueio.setMotivo(motivo);
        return bloqueio;
    }
}
