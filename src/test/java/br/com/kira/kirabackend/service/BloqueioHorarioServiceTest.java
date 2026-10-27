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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(bloqueioHorarioRepository.findById(id)).thenReturn(Optional.of(new BloqueioHorario()));

        service.remover(id);

        verify(bloqueioHorarioRepository).deleteById(id);
    }

    @Test
    @DisplayName("Remover um bloqueio inexistente dá erro 404")
    void removerBloqueioInexistente() {
        UUID id = UUID.randomUUID();
        when(bloqueioHorarioRepository.findById(id)).thenReturn(Optional.empty());

        RecursoNaoEncontradoException ex = assertThrows(RecursoNaoEncontradoException.class,
                () -> service.remover(id));

        assertEquals("Bloqueio não encontrado", ex.getMessage());
        verify(bloqueioHorarioRepository, never()).deleteById(any());
    }
}
