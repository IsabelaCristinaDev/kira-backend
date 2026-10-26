package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.DisponibilidadeEstudio;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.request.DisponibilidadeRequest;
import br.com.kira.kirabackend.dto.response.DisponibilidadeResponse;
import br.com.kira.kirabackend.dto.response.SlotDisponivel;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.DisponibilidadeEstudioRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional
public class DisponibilidadeService {

    private final DisponibilidadeEstudioRepository disponibilidadeRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ServicoRepository servicoRepository;
    private final UsuarioRepository usuarioRepository;

    public DisponibilidadeResponse cadastrar(UUID empresaId, DisponibilidadeRequest request) {
        Empresa empresa = (Empresa) usuarioRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));
        if (!request.horaFim().isAfter(request.horaInicio())) {
            throw new RegraDeNegocioException("Hora de fim deve ser após hora de início");
        }

        DisponibilidadeEstudio disponibilidade = new DisponibilidadeEstudio();
        disponibilidade.setEmpresa(empresa);
        disponibilidade.setDiaSemana(request.diaSemana());
        disponibilidade.setHoraInicio(request.horaInicio());
        disponibilidade.setHoraFim(request.horaFim());

        DisponibilidadeEstudio salva = disponibilidadeRepository.save(disponibilidade);
        return toResponse(salva);
    }

    public List<DisponibilidadeResponse> listarPorEmpresa(UUID empresaId) {
        return disponibilidadeRepository.findByEmpresaId(empresaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SlotDisponivel> calcularSlotsDisponiveis(
            UUID empresaId,
            UUID servicoId,
            LocalDate data) {

        var servico = servicoRepository.findById(servicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));

        DiaSemana diaSemana = DiaSemana.values()[data.getDayOfWeek().getValue() % 7];

        var disponibilidade = disponibilidadeRepository
                .findByEmpresaIdAndDiaSemana(empresaId, diaSemana)
                .orElseThrow(() -> new RegraDeNegocioException("Empresa não atende neste dia da semana"));

        var agendamentosExistentes = agendamentoRepository
                .findByEmpresaIdAndStatusNotAndDataHoraInicioBeforeAndDataHoraFimAfter(
                        empresaId,
                        StatusAgendamento.CANCELADO,
                        data.plusDays(1).atStartOfDay(),
                        data.atStartOfDay());

        Integer duracaoMinutos = servico.getDuracaoMinutos();
        if (duracaoMinutos == null || duracaoMinutos <= 0) {
            throw new RegraDeNegocioException("Serviço com duração inválida");
        }

        List<SlotDisponivel> slots = new ArrayList<>();

        // LocalDateTime (e não LocalTime) para não "dar a volta" na meia-noite
        // e entrar em loop infinito em expedientes que terminam tarde.
        LocalDateTime cursor = LocalDateTime.of(data, disponibilidade.getHoraInicio());
        LocalDateTime fimExpediente = LocalDateTime.of(data, disponibilidade.getHoraFim());

        while (!cursor.plusMinutes(duracaoMinutos).isAfter(fimExpediente)) {
            LocalDateTime slotInicio = cursor;
            LocalDateTime slotFim = slotInicio.plusMinutes(duracaoMinutos);

            boolean ocupado = agendamentosExistentes.stream()
                    .anyMatch(a ->
                            a.getDataHoraInicio().isBefore(slotFim) &&
                                    a.getDataHoraFim().isAfter(slotInicio));

            if (!ocupado) {
                slots.add(new SlotDisponivel(slotInicio, slotFim));
            }

            cursor = slotFim;
        }

        return slots;
    }

    private DisponibilidadeResponse toResponse(DisponibilidadeEstudio d) {
        return new DisponibilidadeResponse(
                d.getId(),
                d.getDiaSemana(),
                d.getHoraInicio(),
                d.getHoraFim()
        );
    }
}