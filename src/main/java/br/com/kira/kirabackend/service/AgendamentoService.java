package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.*;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
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
import br.com.kira.kirabackend.service.strategy.CancelamentoStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional
public class AgendamentoService {

    private static final String AGENDAMENTO_NAO_ENCONTRADO = "Agendamento não encontrado";

    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicoRepository servicoRepository;
    private final FuncionariaRepository funcionariaRepository;
    private final List<CancelamentoStrategy> cancelamentoStrategies;
    private final ApplicationEventPublisher eventPublisher;

    private CancelamentoStrategy resolverEstrategiaCancelamento(TipoUsuario tipo) {
        return cancelamentoStrategies.stream()
                .filter(strategy -> strategy.getTipo() == tipo)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhuma estratégia de cancelamento encontrada para o tipo: " + tipo));
    }

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
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));

        resolverEstrategiaCancelamento(TipoUsuario.CLIENTE).validarCancelamento(agendamento);

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(this, salvo));
        return toResponse(salvo);
    }

    public AgendamentoResponse cancelarPelaEmpresa(UUID agendamentoId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));

        resolverEstrategiaCancelamento(TipoUsuario.EMPRESA).validarCancelamento(agendamento);

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(this, salvo));
        return toResponse(salvo);
    }

    public AgendamentoResponse concluir(UUID agendamentoId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));

        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AtendimentoConcluidoEvent(this, salvo));
        return toResponse(salvo);
    }

    public AgendamentoResponse reagendarPeloCliente(UUID agendamentoId,
                                                    ReagendamentoRequest request) {
        return reagendar(agendamentoId, request);
    }

    public AgendamentoResponse reagendarPelaEmpresa(UUID agendamentoId,
                                                    ReagendamentoRequest request) {
        return reagendar(agendamentoId, request);
    }

    private AgendamentoResponse reagendar(UUID agendamentoId, ReagendamentoRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO ||
                agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Não é possível reagendar este agendamento");
        }

        LocalDateTime novaDataHoraFim = request.novaDataHoraInicio()
                .plusMinutes(agendamento.getServico().getDuracaoMinutos());

        agendamento.setDataHoraInicio(request.novaDataHoraInicio());
        agendamento.setDataHoraFim(novaDataHoraFim);
        agendamento.setStatus(StatusAgendamento.REAGENDADO);
        agendamento.setLembreteEnviado(false);

        return toResponse(agendamentoRepository.save(agendamento));
    }
    public List<AgendamentoResponse> historicoCliente(UUID clienteId) {
        return agendamentoRepository
                .findByClienteIdAndStatusOrderByDataHoraInicioDesc(
                        clienteId, StatusAgendamento.CONCLUIDO)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AgendamentoResponse toResponse(Agendamento a) {
        return AgendamentoResponse.from(a);
    }
}
