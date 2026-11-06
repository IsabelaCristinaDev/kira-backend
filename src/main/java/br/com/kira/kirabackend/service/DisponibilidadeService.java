package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.DisponibilidadeEstudio;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
import br.com.kira.kirabackend.dto.request.DisponibilidadeRequest;
import br.com.kira.kirabackend.dto.response.DisponibilidadeResponse;
import br.com.kira.kirabackend.dto.response.SlotDisponivel;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.DisponibilidadeEstudioRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional
public class DisponibilidadeService {

    private final DisponibilidadeEstudioRepository disponibilidadeRepository;
    private final ServicoRepository servicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final OcupacaoService ocupacaoService;

    private static void exigirDono(UUID empresaId, UUID usuarioId) {
        if (!empresaId.equals(usuarioId)) {
            throw new RegraDeNegocioException("Você só pode alterar o horário do seu estabelecimento");
        }
    }

    /**
     * Cadastra o expediente de um dia. Se o dia já tiver expediente, ele é atualizado:
     * o cálculo de horários livres espera no máximo um registro por dia.
     * Só a própria empresa pode alterar o seu horário.
     */
    public DisponibilidadeResponse cadastrar(UUID empresaId, UUID usuarioId, DisponibilidadeRequest request) {
        exigirDono(empresaId, usuarioId);
        Empresa empresa = buscarEmpresa(empresaId);
        validarHorario(request);

        DisponibilidadeEstudio disponibilidade = disponibilidadeRepository
                .findByEmpresaIdAndDiaSemana(empresaId, request.diaSemana())
                .orElseGet(DisponibilidadeEstudio::new);
        disponibilidade.setEmpresa(empresa);
        disponibilidade.setDiaSemana(request.diaSemana());
        disponibilidade.setHoraInicio(request.horaInicio());
        disponibilidade.setHoraFim(request.horaFim());

        DisponibilidadeEstudio salva = disponibilidadeRepository.save(disponibilidade);
        return toResponse(salva);
    }

    /**
     * Troca o horário de funcionamento da semana inteira. Dia fora da lista fica fechado.
     * Só a própria empresa pode alterar o seu horário.
     */
    public List<DisponibilidadeResponse> substituirSemana(
            UUID empresaId,
            UUID usuarioId,
            List<DisponibilidadeRequest> dias) {

        exigirDono(empresaId, usuarioId);
        Empresa empresa = buscarEmpresa(empresaId);

        Set<DiaSemana> vistos = EnumSet.noneOf(DiaSemana.class);
        for (DisponibilidadeRequest dia : dias) {
            validarHorario(dia);
            if (!vistos.add(dia.diaSemana())) {
                throw new RegraDeNegocioException("Dia da semana repetido: " + dia.diaSemana());
            }
        }

        disponibilidadeRepository.deleteAll(disponibilidadeRepository.findByEmpresaId(empresaId));
        // Apaga antes de inserir: sem o flush o Hibernate faria os inserts primeiro
        disponibilidadeRepository.flush();

        List<DisponibilidadeEstudio> novos = dias.stream().map(dia -> {
            DisponibilidadeEstudio disponibilidade = new DisponibilidadeEstudio();
            disponibilidade.setEmpresa(empresa);
            disponibilidade.setDiaSemana(dia.diaSemana());
            disponibilidade.setHoraInicio(dia.horaInicio());
            disponibilidade.setHoraFim(dia.horaFim());
            return disponibilidade;
        }).toList();

        return disponibilidadeRepository.saveAll(novos)
                .stream()
                .sorted(Comparator.comparing(DisponibilidadeEstudio::getDiaSemana))
                .map(this::toResponse)
                .toList();
    }

    public List<DisponibilidadeResponse> listarPorEmpresa(UUID empresaId) {
        exigirEmpresaAtiva(empresaId);
        return disponibilidadeRepository.findByEmpresaId(empresaId)
                .stream()
                .sorted(Comparator.comparing(DisponibilidadeEstudio::getDiaSemana))
                .map(this::toResponse)
                .toList();
    }

    private Empresa buscarEmpresa(UUID empresaId) {
        return usuarioRepository.findById(empresaId)
                .filter(Empresa.class::isInstance)
                .map(Empresa.class::cast)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));
    }

    private static void validarHorario(DisponibilidadeRequest request) {
        if (!request.horaFim().isAfter(request.horaInicio())) {
            throw new RegraDeNegocioException("Hora de fim deve ser após hora de início");
        }
    }

    /**
     * Horários livres do dia para o serviço. Com {@code funcionariaId}, só os horários em que ela
     * está livre; sem, os horários em que alguma funcionária (ou o estúdio, se não tiver) está livre.
     * Horário que já começou fica de fora, porque o agendamento recusaria.
     */
    public List<SlotDisponivel> calcularSlotsDisponiveis(
            UUID empresaId,
            UUID servicoId,
            LocalDate data,
            UUID funcionariaId) {
        return calcularSlotsDisponiveis(empresaId, servicoId, data, funcionariaId,
                LocalDateTime.now(KiraTimeZone.DEFAULT));
    }

    /** Recebe o momento atual para os testes não dependerem do relógio. */
    List<SlotDisponivel> calcularSlotsDisponiveis(
            UUID empresaId,
            UUID servicoId,
            LocalDate data,
            UUID funcionariaId,
            LocalDateTime agora) {
        exigirEmpresaAtiva(empresaId);

        var servico = servicoRepository.findById(servicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));

        DiaSemana diaSemana = DiaSemana.values()[data.getDayOfWeek().getValue() % 7];

        var disponibilidade = disponibilidadeRepository
                .findByEmpresaIdAndDiaSemana(empresaId, diaSemana)
                .orElseThrow(() -> new RegraDeNegocioException("Empresa não atende neste dia da semana"));

        QuadroDeHorarios quadro = ocupacaoService.carregar(
                empresaId, data.atStartOfDay(), data.plusDays(1).atStartOfDay(), null);
        if (funcionariaId != null && !quadro.temFuncionaria(funcionariaId)) {
            throw new RegraDeNegocioException("Funcionária não atende neste estabelecimento");
        }

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

            // Mesmo critério do agendamento: precisa começar depois de agora
            if (slotInicio.isAfter(agora) && quadro.livre(funcionariaId, slotInicio, slotFim)) {
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

    // Empresa desativada responde como inexistente, igual à vitrine
    private void exigirEmpresaAtiva(UUID empresaId) {
        if (!usuarioRepository.existsEmpresaAtivaById(empresaId)) {
            throw new RecursoNaoEncontradoException("Empresa não encontrada");
        }
    }
}