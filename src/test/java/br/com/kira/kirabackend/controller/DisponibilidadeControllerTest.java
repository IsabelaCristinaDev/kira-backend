package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
import br.com.kira.kirabackend.dto.request.DisponibilidadeRequest;
import br.com.kira.kirabackend.service.DisponibilidadeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DisponibilidadeControllerTest {

    @Mock
    private DisponibilidadeService disponibilidadeService;

    @InjectMocks
    private DisponibilidadeController controller;

    @Test
    @DisplayName("Cadastrar passa para o service o usuário logado, não só o id da URL")
    void cadastrarUsaOUsuarioLogado() {
        UUID empresaDaUrl = UUID.randomUUID();
        Cliente usuarioLogado = new Cliente();
        usuarioLogado.setId(UUID.randomUUID());
        DisponibilidadeRequest request =
                new DisponibilidadeRequest(DiaSemana.SEGUNDA, LocalTime.of(9, 0), LocalTime.of(18, 0));

        controller.cadastrar(empresaDaUrl, usuarioLogado, request);

        verify(disponibilidadeService).cadastrar(empresaDaUrl, usuarioLogado.getId(), request);
    }
}
