package br.com.kira.kirabackend.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record DashboardEmpresaResponse(
        BigDecimal faturamentoTotal,
        int totalAtendimentos,
        List<FuncionariaDesempenhoResponse> desempenhoPorFuncionaria,
        int totalClientesUnicos,
        int totalClientesRecorrentes,
        double percentualRecorrencia,
        List<ClienteRecorrenteResponse> clientesRecorrentes,
        List<MovimentoPorHoraResponse> movimentoPorHora,
        List<MovimentoPorDiaSemanaResponse> movimentoPorDiaSemana,
        BigDecimal ticketMedio,
        int totalCancelados,
        double taxaConclusao,
        BigDecimal faturamentoPeriodoAnterior,
        Double variacaoFaturamento,
        List<FaturamentoPorDiaResponse> faturamentoPorDia,
        List<ServicoRankingResponse> rankingServicos,
        AvaliacoesResumoResponse avaliacoes
) {}
