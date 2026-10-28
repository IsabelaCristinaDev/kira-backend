package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.BloqueioHorario;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.BloqueioHorarioRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Carrega do banco o que ocupa a agenda de um estúdio num intervalo: agendamentos não
 * cancelados, funcionárias ativas e os bloqueios delas.
 */
@RequiredArgsConstructor
@Service
public class OcupacaoService {

    private final AgendamentoRepository agendamentoRepository;
    private final FuncionariaRepository funcionariaRepository;
    private final BloqueioHorarioRepository bloqueioHorarioRepository;

    /** No reagendamento, {@code agendamentoIgnorado} é o próprio agendamento, que não conta como ocupado. */
    public QuadroDeHorarios carregar(UUID empresaId, LocalDateTime de, LocalDateTime ate, UUID agendamentoIgnorado) {
        List<Agendamento> agendamentos = agendamentoRepository
                .findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
                        empresaId, StatusAgendamento.CANCELADO, ate, de)
                .stream()
                .filter(a -> !a.getId().equals(agendamentoIgnorado))
                .toList();

        List<Funcionaria> funcionarias = funcionariaRepository.findByEmpresaIdAndAtivoTrue(empresaId)
                .stream()
                .sorted(Comparator.comparing(Funcionaria::getNome))
                .toList();

        List<BloqueioHorario> bloqueios = funcionarias.isEmpty()
                ? List.of()
                : bloqueioHorarioRepository.findByFuncionaria_Empresa_IdAndDataHoraInicioBeforeAndDataHoraFimAfter(
                        empresaId, ate, de);

        return new QuadroDeHorarios(funcionarias, agendamentos, bloqueios);
    }
}
