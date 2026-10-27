package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.response.ClienteRecorrenteResponse;
import br.com.kira.kirabackend.dto.response.DashboardEmpresaResponse;
import br.com.kira.kirabackend.dto.response.FuncionariaDesempenhoResponse;
import br.com.kira.kirabackend.dto.response.MovimentoPorDiaSemanaResponse;
import br.com.kira.kirabackend.dto.response.MovimentoPorHoraResponse;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardEmpresaService {

    private final AgendamentoRepository agendamentoRepository;

    public DashboardEmpresaService(AgendamentoRepository agendamentoRepository) {
        this.agendamentoRepository = agendamentoRepository;
    }

    public DashboardEmpresaResponse gerar(UUID empresaId, LocalDate dataInicio, LocalDate dataFim) {
        LocalDate inicio = dataInicio != null ? dataInicio
                : LocalDate.now(KiraTimeZone.DEFAULT).withDayOfMonth(1);
        LocalDate fim = dataFim != null ? dataFim
                : LocalDate.now(KiraTimeZone.DEFAULT);

        List<Agendamento> agendamentos = agendamentoRepository
                .findByEmpresaIdAndStatusAndDataHoraInicioBetween(
                        empresaId,
                        StatusAgendamento.CONCLUIDO,
                        inicio.atStartOfDay(),
                        fim.atTime(LocalTime.MAX));

        BigDecimal faturamentoTotal = somarPreco(agendamentos);

        Map<UUID, List<Agendamento>> porFuncionaria = new LinkedHashMap<>();
        for (Agendamento a : agendamentos) {
            UUID funcionariaId = a.getFuncionaria() != null ? a.getFuncionaria().getId() : null;
            porFuncionaria.computeIfAbsent(funcionariaId, k -> new ArrayList<>()).add(a);
        }

        List<FuncionariaDesempenhoResponse> desempenhoPorFuncionaria = porFuncionaria
                .entrySet().stream()
                .map(entry -> {
                    List<Agendamento> doGrupo = entry.getValue();
                    Agendamento primeiro = doGrupo.get(0);
                    return new FuncionariaDesempenhoResponse(
                            entry.getKey(),
                            primeiro.getFuncionaria() != null ? primeiro.getFuncionaria().getNome() : null,
                            somarPreco(doGrupo),
                            doGrupo.size());
                })
                .sorted(Comparator.comparing(FuncionariaDesempenhoResponse::faturamento).reversed())
                .toList();

        Map<UUID, List<Agendamento>> porCliente = agendamentos.stream()
                .collect(Collectors.groupingBy(a -> a.getCliente().getId()));

        List<ClienteRecorrenteResponse> clientesRecorrentes = porCliente.values().stream()
                .filter(doGrupo -> doGrupo.size() >= 2)
                .map(doGrupo -> {
                    Agendamento primeiro = doGrupo.get(0);
                    return new ClienteRecorrenteResponse(
                            primeiro.getCliente().getId(),
                            primeiro.getCliente().getNome(),
                            doGrupo.size(),
                            somarPreco(doGrupo));
                })
                .sorted(Comparator.comparing(ClienteRecorrenteResponse::totalAtendimentos).reversed())
                .toList();

        int totalClientesUnicos = porCliente.size();
        int totalClientesRecorrentes = clientesRecorrentes.size();
        double percentualRecorrencia = totalClientesUnicos == 0 ? 0.0
                : (totalClientesRecorrentes * 100.0) / totalClientesUnicos;

        List<MovimentoPorHoraResponse> movimentoPorHora = agendamentos.stream()
                .collect(Collectors.groupingBy(a -> a.getDataHoraInicio().getHour(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new MovimentoPorHoraResponse(entry.getKey(), entry.getValue().intValue()))
                .sorted(Comparator.comparing(MovimentoPorHoraResponse::hora))
                .toList();

        List<MovimentoPorDiaSemanaResponse> movimentoPorDiaSemana = agendamentos.stream()
                .collect(Collectors.groupingBy(a -> a.getDataHoraInicio().getDayOfWeek(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new MovimentoPorDiaSemanaResponse(entry.getKey(), entry.getValue().intValue()))
                .sorted(Comparator.comparing((MovimentoPorDiaSemanaResponse r) -> r.diaSemana().getValue()))
                .toList();

        return new DashboardEmpresaResponse(
                faturamentoTotal,
                agendamentos.size(),
                desempenhoPorFuncionaria,
                totalClientesUnicos,
                totalClientesRecorrentes,
                percentualRecorrencia,
                clientesRecorrentes,
                movimentoPorHora,
                movimentoPorDiaSemana);
    }

    private BigDecimal somarPreco(List<Agendamento> agendamentos) {
        return agendamentos.stream()
                .map(a -> a.getServico().getPreco())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
