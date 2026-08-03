package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
@RequiredArgsConstructor
@Service
@Transactional
public class AgendaFuncionariaService {

    private final AgendamentoRepository agendamentoRepository;

    public List<AgendamentoResponse> listarPorDia(UUID funcionariaId, LocalDate data) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.atTime(LocalTime.MAX);

        return agendamentoRepository
                .findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                        funcionariaId, inicio, fim)
                .stream()
                .map(AgendamentoResponse::from)
                .toList();
    }

    public List<AgendamentoResponse> listarPorSemana(UUID funcionariaId,
                                                     LocalDate dataInicio) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataInicio.plusDays(6).atTime(LocalTime.MAX);

        return agendamentoRepository
                .findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                        funcionariaId, inicio, fim)
                .stream()
                .map(AgendamentoResponse::from)
                .toList();
    }
}