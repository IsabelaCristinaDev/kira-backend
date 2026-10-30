package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import br.com.kira.kirabackend.dto.response.AvaliacoesResumoResponse;
import br.com.kira.kirabackend.dto.response.ClienteDoServicoResponse;
import br.com.kira.kirabackend.dto.response.ClienteRecorrenteResponse;
import br.com.kira.kirabackend.dto.response.DashboardEmpresaResponse;
import br.com.kira.kirabackend.dto.response.FaturamentoPorDiaResponse;
import br.com.kira.kirabackend.dto.response.FuncionariaDesempenhoResponse;
import br.com.kira.kirabackend.dto.response.MovimentoPorDiaSemanaResponse;
import br.com.kira.kirabackend.dto.response.MovimentoPorHoraResponse;
import br.com.kira.kirabackend.dto.response.NotaQuantidadeResponse;
import br.com.kira.kirabackend.dto.response.ServicoRankingResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
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
    private final AvaliacaoRepository avaliacaoRepository;

    public DashboardEmpresaService(AgendamentoRepository agendamentoRepository,
                                   AvaliacaoRepository avaliacaoRepository) {
        this.agendamentoRepository = agendamentoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
    }

    public DashboardEmpresaResponse gerar(UUID empresaId, LocalDate dataInicio, LocalDate dataFim) {
        LocalDate inicio = dataInicio != null ? dataInicio
                : LocalDate.now(KiraTimeZone.DEFAULT).withDayOfMonth(1);
        LocalDate fim = dataFim != null ? dataFim
                : LocalDate.now(KiraTimeZone.DEFAULT);
        if (fim.isBefore(inicio)) {
            throw new RegraDeNegocioException("A data de fim deve ser igual ou posterior à data de início");
        }

        List<Agendamento> agendamentos = concluidos(empresaId, inicio, fim);

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

        long totalCancelados = agendamentoRepository.countByEmpresaIdAndStatusAndDataHoraInicioBetween(
                empresaId, StatusAgendamento.CANCELADO, inicio.atStartOfDay(), fim.atTime(LocalTime.MAX));
        double taxaConclusao = agendamentos.isEmpty() && totalCancelados == 0 ? 0.0
                : (agendamentos.size() * 100.0) / (agendamentos.size() + totalCancelados);

        // Período anterior com o mesmo número de dias, terminando na véspera do início
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        BigDecimal faturamentoPeriodoAnterior = somarPreco(
                concluidos(empresaId, inicio.minusDays(dias), inicio.minusDays(1)));

        return new DashboardEmpresaResponse(
                faturamentoTotal,
                agendamentos.size(),
                desempenhoPorFuncionaria,
                totalClientesUnicos,
                totalClientesRecorrentes,
                percentualRecorrencia,
                clientesRecorrentes,
                movimentoPorHora,
                movimentoPorDiaSemana,
                ticketMedio(faturamentoTotal, agendamentos.size()),
                (int) totalCancelados,
                taxaConclusao,
                faturamentoPeriodoAnterior,
                variacao(faturamentoTotal, faturamentoPeriodoAnterior),
                faturamentoPorDia(agendamentos),
                rankingServicos(agendamentos),
                resumoAvaliacoes(empresaId));
    }

    private List<Agendamento> concluidos(UUID empresaId, LocalDate inicio, LocalDate fim) {
        return agendamentoRepository.findByEmpresaIdAndStatusAndDataHoraInicioBetween(
                empresaId,
                StatusAgendamento.CONCLUIDO,
                inicio.atStartOfDay(),
                fim.atTime(LocalTime.MAX));
    }

    private static BigDecimal ticketMedio(BigDecimal faturamento, int atendimentos) {
        return atendimentos == 0 ? BigDecimal.ZERO
                : faturamento.divide(BigDecimal.valueOf(atendimentos), 2, RoundingMode.HALF_UP);
    }

    // Variação % contra o período anterior; null quando o anterior não faturou nada
    private static Double variacao(BigDecimal atual, BigDecimal anterior) {
        if (anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(BigDecimal.valueOf(100))
                .divide(anterior, 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<FaturamentoPorDiaResponse> faturamentoPorDia(List<Agendamento> agendamentos) {
        return agendamentos.stream()
                .collect(Collectors.groupingBy(a -> a.getDataHoraInicio().toLocalDate()))
                .entrySet().stream()
                .map(entry -> new FaturamentoPorDiaResponse(
                        entry.getKey(), somarPreco(entry.getValue()), entry.getValue().size()))
                .sorted(Comparator.comparing(FaturamentoPorDiaResponse::data))
                .toList();
    }

    // Serviços mais feitos, cada um com os clientes que o fizeram
    private List<ServicoRankingResponse> rankingServicos(List<Agendamento> agendamentos) {
        return agendamentos.stream()
                .collect(Collectors.groupingBy(a -> a.getServico().getId()))
                .values().stream()
                .map(doServico -> new ServicoRankingResponse(
                        doServico.get(0).getServico().getId(),
                        doServico.get(0).getServico().getNome(),
                        doServico.size(),
                        somarPreco(doServico),
                        clientesDoServico(doServico)))
                .sorted(Comparator.comparingInt(ServicoRankingResponse::totalAtendimentos).reversed()
                        .thenComparing(ServicoRankingResponse::faturamento, Comparator.reverseOrder()))
                .toList();
    }

    private List<ClienteDoServicoResponse> clientesDoServico(List<Agendamento> doServico) {
        return doServico.stream()
                .collect(Collectors.groupingBy(a -> a.getCliente().getId()))
                .values().stream()
                .map(doCliente -> new ClienteDoServicoResponse(
                        doCliente.get(0).getCliente().getId(),
                        doCliente.get(0).getCliente().getNome(),
                        doCliente.size(),
                        somarPreco(doCliente),
                        doCliente.stream().map(Agendamento::getDataHoraInicio)
                                .max(Comparator.naturalOrder()).orElseThrow()))
                .sorted(Comparator.comparingInt(ClienteDoServicoResponse::totalAtendimentos).reversed()
                        .thenComparing(ClienteDoServicoResponse::ultimoAtendimento, Comparator.reverseOrder()))
                .toList();
    }

    // Avaliações de clientes de todos os tempos: a nota do estúdio não depende do período
    private AvaliacoesResumoResponse resumoAvaliacoes(UUID empresaId) {
        List<Avaliacao> avaliacoes = avaliacaoRepository
                .findByEmpresaIdAndTipoAvaliador(empresaId, TipoAvaliador.CLIENTE);
        Double media = avaliacoes.isEmpty() ? null
                : avaliacoes.stream().mapToInt(Avaliacao::getNota).average().orElseThrow();
        Map<Integer, Long> porNota = avaliacoes.stream()
                .collect(Collectors.groupingBy(Avaliacao::getNota, Collectors.counting()));
        List<NotaQuantidadeResponse> distribuicao = List.of(5, 4, 3, 2, 1).stream()
                .map(nota -> new NotaQuantidadeResponse(nota, porNota.getOrDefault(nota, 0L).intValue()))
                .toList();
        return new AvaliacoesResumoResponse(media, avaliacoes.size(), distribuicao);
    }

    private BigDecimal somarPreco(List<Agendamento> agendamentos) {
        return agendamentos.stream()
                .map(a -> a.getServico().getPreco())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
