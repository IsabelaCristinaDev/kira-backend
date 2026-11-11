package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.ComissaoAgendamento;
import br.com.kira.kirabackend.dto.response.ComissaoResponse;
import br.com.kira.kirabackend.repository.ComissaoAgendamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ComissaoService {

    private final ComissaoAgendamentoRepository comissaoRepository;

    public void calcularERegistrarComissao(Agendamento agendamento) {
        if (agendamento.getFuncionaria() == null) return;
        if (agendamento.getFuncionaria().getComissaoPercentual() == null) return;

        BigDecimal valorServico = agendamento.getServico().getPreco();
        BigDecimal percentual = agendamento.getFuncionaria().getComissaoPercentual();

        BigDecimal valorComissao = valorServico
                .multiply(percentual)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        ComissaoAgendamento comissao = new ComissaoAgendamento();
        comissao.setAgendamento(agendamento);
        comissao.setFuncionaria(agendamento.getFuncionaria());
        comissao.setValorServico(valorServico);
        comissao.setPercentualComissao(percentual);
        comissao.setValorComissao(valorComissao);

        comissaoRepository.save(comissao);
    }

    // Converte aqui, dentro da transação: com open-in-view desligado, as relações LAZY não carregam no controller
    public List<ComissaoResponse> listarPorFuncionaria(UUID funcionariaId) {
        return comissaoRepository.findByFuncionariaId(funcionariaId)
                .stream()
                .map(ComissaoResponse::from)
                .toList();
    }

    public BigDecimal totalComissaoFuncionaria(UUID funcionariaId) {
        BigDecimal total = comissaoRepository.calcularTotalComissaoFuncionaria(funcionariaId);
        return total != null ? total : BigDecimal.ZERO;
    }
}