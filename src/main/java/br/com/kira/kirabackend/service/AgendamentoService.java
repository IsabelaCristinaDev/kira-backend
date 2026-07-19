package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.*;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.request.AgendamentoRequest;
import br.com.kira.kirabackend.dto.request.ReagendamentoRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.observer.AgendamentoCanceladoEvent;
import br.com.kira.kirabackend.observer.AgendamentoCriadoEvent;
import br.com.kira.kirabackend.observer.AtendimentoConcluidoEvent;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.service.strategy.CancelamentoClienteStrategy;
import br.com.kira.kirabackend.service.strategy.CancelamentoEmpresaStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AgendamentoService {

    @Autowired
    private AgendamentoRepository agendamentoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ServicoRepository servicoRepository;

    @Autowired
    private FuncionariaRepository funcionariaRepository;

    @Autowired
    private CancelamentoClienteStrategy cancelamentoClienteStrategy;

    @Autowired
    private CancelamentoEmpresaStrategy cancelamentoEmpresaStrategy;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public AgendamentoResponse criar(UUID clienteId, AgendamentoRequest request) {
        Cliente cliente = (Cliente) usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));

        Empresa empresa = (Empresa) usuarioRepository.findById(request.empresaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        Servico servico = servicoRepository.findById(request.servicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));

        Funcionaria funcionaria = null;
        if (request.funcionariaId() != null) {
            funcionaria = funcionariaRepository.findById(request.funcionariaId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionária não encontrada"));
        }

        LocalDateTime dataHoraFim = request.dataHoraInicio()
                .plusMinutes(servico.getDuracaoMinutos());

        Agendamento agendamento = new Agendamento();
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setFuncionaria(funcionaria);
        agendamento.setServico(servico);
        agendamento.setDataHoraInicio(request.dataHoraInicio());
        agendamento.setDataHoraFim(dataHoraFim);
        agendamento.setFormaPagamento(request.formaPagamento());
        agendamento.setObservacoes(request.observacoes());

        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoCriadoEvent(this, salvo));
        return toResponse(salvo);
    }

    public List<AgendamentoResponse> listarPorCliente(UUID clienteId) {
        return agendamentoRepository.findByClienteId(clienteId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AgendamentoResponse> listarPorEmpresa(UUID empresaId) {
        return agendamentoRepository.findByEmpresaId(empresaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AgendamentoResponse cancelarPeloCliente(UUID agendamentoId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));

        cancelamentoClienteStrategy.validarCancelamento(agendamento);

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(this, salvo));
        return toResponse(salvo);
    }

    public AgendamentoResponse cancelarPelaEmpresa(UUID agendamentoId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));

        cancelamentoEmpresaStrategy.validarCancelamento(agendamento);

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(this, salvo));
        return toResponse(salvo);
    }

    public AgendamentoResponse concluir(UUID agendamentoId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));

        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AtendimentoConcluidoEvent(this, salvo));
        return toResponse(salvo);
    }

    public AgendamentoResponse reagendarPeloCliente(UUID agendamentoId,
                                                    ReagendamentoRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO ||
                agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Não é possível reagendar este agendamento");
        }

        LocalDateTime novaDataHoraFim = request.novaDataHoraInicio()
                .plusMinutes(agendamento.getServico().getDuracaoMinutos());

        agendamento.setDataHoraInicio(request.novaDataHoraInicio());
        agendamento.setDataHoraFim(novaDataHoraFim);
        agendamento.setStatus(StatusAgendamento.REAGENDADO);

        return toResponse(agendamentoRepository.save(agendamento));
    }

    public AgendamentoResponse reagendarPelaEmpresa(UUID agendamentoId,
                                                    ReagendamentoRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO ||
                agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Não é possível reagendar este agendamento");
        }

        LocalDateTime novaDataHoraFim = request.novaDataHoraInicio()
                .plusMinutes(agendamento.getServico().getDuracaoMinutos());

        agendamento.setDataHoraInicio(request.novaDataHoraInicio());
        agendamento.setDataHoraFim(novaDataHoraFim);
        agendamento.setStatus(StatusAgendamento.REAGENDADO);

        return toResponse(agendamentoRepository.save(agendamento));
    }

    private AgendamentoResponse toResponse(Agendamento a) {
        return new AgendamentoResponse(
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
        );
    }
}