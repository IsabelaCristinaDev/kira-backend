package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AgendaFuncionariaService {

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    public List<AgendamentoResponse> listarPorDia(UUID funcionariaId, LocalDate data) {
        LocalDateTime inicio = data.atStartOfDay();
        LocalDateTime fim = data.atTime(LocalTime.MAX);

        return agendamentoRepository
                .findByFuncionariaIdAndDataHoraInicioBetweenOrderByDataHoraInicio(
                        funcionariaId, inicio, fim)
                .stream()
                .map(a -> new AgendamentoResponse(
                        a.getId(),
                        a.getCliente().getId(),
                        a.getCliente().getNome(),
                        a.getEmpresa().getId(),
                        a.getEmpresa().getNome(),
                        a.getFuncionaria() != null ? a.getFuncionaria().getId() : null,
                        a.getFuncionaria() != null ? a.getFuncionaria().getNome() : null,
                        a.getServico().getId(),
                        a.getServico().getNome(),
                        a.getServico().getDuracaoMinutos(),
                        a.getDataHoraInicio(),
                        a.getDataHoraFim(),
                        a.getStatus(),
                        a.getFormaPagamento(),
                        a.getObservacoes()
                ))
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
                .map(a -> new AgendamentoResponse(
                        a.getId(),
                        a.getCliente().getId(),
                        a.getCliente().getNome(),
                        a.getEmpresa().getId(),
                        a.getEmpresa().getNome(),
                        a.getFuncionaria() != null ? a.getFuncionaria().getId() : null,
                        a.getFuncionaria() != null ? a.getFuncionaria().getNome() : null,
                        a.getServico().getId(),
                        a.getServico().getNome(),
                        a.getServico().getDuracaoMinutos(),
                        a.getDataHoraInicio(),
                        a.getDataHoraFim(),
                        a.getStatus(),
                        a.getFormaPagamento(),
                        a.getObservacoes()
                ))
                .toList();
    }
}