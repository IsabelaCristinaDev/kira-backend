package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.BloqueioHorario;
import br.com.kira.kirabackend.domain.entity.Funcionaria;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ocupação da agenda de um estúdio, já carregada pelo {@link OcupacaoService}.
 * <p>
 * Estúdio sem funcionárias ativas atende um cliente por vez. Com funcionárias, cada uma atende
 * um por vez e não atende durante os seus bloqueios. Agendamento sem funcionária ativa (antigo
 * ou de funcionária desativada) ocupa a vaga de uma funcionária qualquer.
 * <p>
 * Os atendimentos carregados também servem para dividir o trabalho: sem funcionária pedida,
 * fica quem tem menos atendimentos no período carregado (o dia, no agendamento).
 */
public class QuadroDeHorarios {

    private final List<Funcionaria> funcionarias;
    private final List<Agendamento> agendamentos;
    private final List<BloqueioHorario> bloqueios;
    private final Set<UUID> idsDasFuncionarias;

    QuadroDeHorarios(List<Funcionaria> funcionarias, List<Agendamento> agendamentos,
                     List<BloqueioHorario> bloqueios) {
        this.funcionarias = funcionarias;
        this.agendamentos = agendamentos;
        this.bloqueios = bloqueios;
        this.idsDasFuncionarias = funcionarias.stream().map(Funcionaria::getId).collect(Collectors.toSet());
    }

    public boolean temFuncionarias() {
        return !funcionarias.isEmpty();
    }

    /** Se é uma funcionária ativa deste estúdio. */
    public boolean temFuncionaria(UUID funcionariaId) {
        return idsDasFuncionarias.contains(funcionariaId);
    }

    /** Se cabe um atendimento no intervalo, com a funcionária pedida ou, sem pedido, com qualquer uma. */
    public boolean livre(UUID funcionariaId, LocalDateTime inicio, LocalDateTime fim) {
        if (!temFuncionarias()) {
            return agendamentos.stream().noneMatch(a -> cruza(a.getDataHoraInicio(), a.getDataHoraFim(), inicio, fim));
        }
        return funcionariaLivre(funcionariaId, inicio, fim).isPresent();
    }

    /**
     * Quem atende no intervalo: a funcionária pedida, se estiver livre, ou, sem pedido, a livre com
     * menos atendimentos no período carregado (empate: ordem de nome). Vazio quando não cabe.
     * Só faz sentido com {@link #temFuncionarias()}.
     */
    public Optional<Funcionaria> funcionariaLivre(UUID funcionariaId, LocalDateTime inicio, LocalDateTime fim) {
        List<Funcionaria> livres = funcionarias.stream()
                .filter(f -> semCompromisso(f.getId(), inicio, fim))
                .toList();
        long semFuncionaria = agendamentos.stream()
                .filter(a -> cruza(a.getDataHoraInicio(), a.getDataHoraFim(), inicio, fim))
                .filter(a -> a.getFuncionaria() == null || !temFuncionaria(a.getFuncionaria().getId()))
                .count();
        if (livres.size() <= semFuncionaria) {
            return Optional.empty();
        }
        if (funcionariaId != null) {
            return livres.stream().filter(f -> f.getId().equals(funcionariaId)).findFirst();
        }
        return livres.stream()
                .min(Comparator.comparingLong((Funcionaria f) -> atendimentosDa(f.getId()))
                        .thenComparing(Funcionaria::getNome));
    }

    private long atendimentosDa(UUID funcionariaId) {
        return agendamentos.stream()
                .filter(a -> a.getFuncionaria() != null && a.getFuncionaria().getId().equals(funcionariaId))
                .count();
    }

    private boolean semCompromisso(UUID funcionariaId, LocalDateTime inicio, LocalDateTime fim) {
        boolean agendada = agendamentos.stream()
                .anyMatch(a -> a.getFuncionaria() != null
                        && a.getFuncionaria().getId().equals(funcionariaId)
                        && cruza(a.getDataHoraInicio(), a.getDataHoraFim(), inicio, fim));
        boolean bloqueada = bloqueios.stream()
                .anyMatch(b -> b.getFuncionaria().getId().equals(funcionariaId)
                        && cruza(b.getDataHoraInicio(), b.getDataHoraFim(), inicio, fim));
        return !agendada && !bloqueada;
    }

    /** Intervalos [início, fim) se sobrepõem. */
    private static boolean cruza(LocalDateTime aInicio, LocalDateTime aFim, LocalDateTime inicio, LocalDateTime fim) {
        return aInicio.isBefore(fim) && aFim.isAfter(inicio);
    }
}
