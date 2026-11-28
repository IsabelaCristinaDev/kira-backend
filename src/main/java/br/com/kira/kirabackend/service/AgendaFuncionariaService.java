package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@RequiredArgsConstructor
@Service
@Transactional
public class AgendaFuncionariaService {

    private final AgendamentoRepository agendamentoRepository;

    public List<AgendamentoResponse> listarPorDia(UUID funcionariaId, LocalDate data) {
        return listar(funcionariaId, data, 1);
    }

    public List<AgendamentoResponse> listarPorSemana(UUID funcionariaId, LocalDate dataInicio) {
        return listar(funcionariaId, dataInicio, 7);
    }

    // Do início do primeiro dia até a meia-noite seguinte ao último, que fica de fora
    private List<AgendamentoResponse> listar(UUID funcionariaId, LocalDate primeiroDia, int dias) {
        LocalDateTime inicio = primeiroDia.atStartOfDay();
        LocalDateTime fim = primeiroDia.plusDays(dias).atStartOfDay();

        return agendamentoRepository.listarDaFuncionariaNoPeriodo(funcionariaId, inicio, fim)
                .stream()
                .map(AgendamentoResponse::from)
                .toList();
    }
}