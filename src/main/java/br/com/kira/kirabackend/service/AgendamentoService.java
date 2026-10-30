package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.*;
import br.com.kira.kirabackend.domain.enums.DiaSemana;
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
import br.com.kira.kirabackend.repository.DisponibilidadeEstudioRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.service.strategy.CancelamentoClienteStrategy;
import br.com.kira.kirabackend.service.strategy.CancelamentoStrategy;
import br.com.kira.kirabackend.util.KiraTimeZone;
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
    private static final String SO_OS_SEUS_AGENDAMENTOS = "Você só pode ver os seus próprios agendamentos";

    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicoRepository servicoRepository;
    private final FuncionariaRepository funcionariaRepository;
    private final DisponibilidadeEstudioRepository disponibilidadeRepository;
    private final List<CancelamentoStrategy> cancelamentoStrategies;
    private final ApplicationEventPublisher eventPublisher;
    private final OcupacaoService ocupacaoService;

    private CancelamentoStrategy resolverEstrategiaCancelamento(TipoUsuario tipo) {
        return cancelamentoStrategies.stream()
                .filter(strategy -> strategy.getTipo() == tipo)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhuma estratégia de cancelamento encontrada para o tipo: " + tipo));
    }

    public AgendamentoResponse criar(UUID clienteId, UUID usuarioId, AgendamentoRequest request) {
        exigirProprioUsuario(clienteId, usuarioId, "Você só pode agendar em seu próprio nome");
        Cliente cliente = (Cliente) usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));

        // Empresa desativada responde como inexistente, igual à vitrine
        Empresa empresa = usuarioRepository.findById(request.empresaId())
                .filter(Empresa.class::isInstance)
                .map(Empresa.class::cast)
                .filter(Empresa::isEnabled)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        Servico servico = servicoRepository.findById(request.servicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));

        if (servico.getEmpresa() == null || !empresa.getId().equals(servico.getEmpresa().getId())) {
            throw new RegraDeNegocioException("Serviço não pertence a este estabelecimento");
        }

        Funcionaria funcionaria = null;
        if (request.funcionariaId() != null) {
            funcionaria = funcionariaRepository.findById(request.funcionariaId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionária não encontrada"));

            if (!empresa.getId().equals(funcionaria.getEmpresa().getId())) {
                throw new RegraDeNegocioException("Funcionária não pertence a este estabelecimento");
            }
            if (Boolean.FALSE.equals(funcionaria.getAtivo())) {
                throw new RegraDeNegocioException("Funcionária não está mais atendendo");
            }
        }

        LocalDateTime dataHoraFim = request.dataHoraInicio()
                .plusMinutes(servico.getDuracaoMinutos());

        validarDataFutura(request.dataHoraInicio());
        validarExpediente(empresa.getId(), request.dataHoraInicio(), dataHoraFim);
        Funcionaria quemAtende = reservarHorario(
                empresa.getId(), funcionaria, request.dataHoraInicio(), dataHoraFim, null);

        Agendamento agendamento = new Agendamento();
        agendamento.setCliente(cliente);
        agendamento.setEmpresa(empresa);
        agendamento.setFuncionaria(quemAtende);
        agendamento.setServico(servico);
        agendamento.setDataHoraInicio(request.dataHoraInicio());
        agendamento.setDataHoraFim(dataHoraFim);
        agendamento.setFormaPagamento(request.formaPagamento());
        agendamento.setObservacoes(request.observacoes());

        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoCriadoEvent(this, salvo));
        return toResponse(salvo);
    }

    public List<AgendamentoResponse> listarPorCliente(UUID clienteId, UUID usuarioId) {
        exigirProprioUsuario(clienteId, usuarioId, SO_OS_SEUS_AGENDAMENTOS);
        return agendamentoRepository.findByClienteId(clienteId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AgendamentoResponse> listarPorEmpresa(UUID empresaId, UUID usuarioId) {
        exigirProprioUsuario(empresaId, usuarioId, SO_OS_SEUS_AGENDAMENTOS);
        return agendamentoRepository.findByEmpresaId(empresaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public AgendamentoResponse cancelarPeloCliente(UUID agendamentoId, UUID clienteId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));
        validarClienteDoAgendamento(agendamento, clienteId);

        resolverEstrategiaCancelamento(TipoUsuario.CLIENTE).validarCancelamento(agendamento);

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(this, salvo));
        return toResponse(salvo);
    }

    public AgendamentoResponse cancelarPelaEmpresa(UUID agendamentoId, UUID empresaId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));
        validarEmpresaDoAgendamento(agendamento, empresaId);

        resolverEstrategiaCancelamento(TipoUsuario.EMPRESA).validarCancelamento(agendamento);

        agendamento.setStatus(StatusAgendamento.CANCELADO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(this, salvo));
        return toResponse(salvo);
    }

    public AgendamentoResponse concluir(UUID agendamentoId, UUID empresaId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));
        validarEmpresaDoAgendamento(agendamento, empresaId);

        if (agendamento.getStatus() == StatusAgendamento.CANCELADO ||
                agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Não é possível concluir este agendamento");
        }

        agendamento.setStatus(StatusAgendamento.CONCLUIDO);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        eventPublisher.publishEvent(new AtendimentoConcluidoEvent(this, salvo));
        return toResponse(salvo);
    }

    /** Só a empresa dona do atendimento pode concluí-lo ou cancelá-lo como empresa. */
    private static void validarEmpresaDoAgendamento(Agendamento agendamento, UUID empresaId) {
        if (!agendamento.getEmpresa().getId().equals(empresaId)) {
            throw new RegraDeNegocioException("Este atendimento não pertence ao seu estabelecimento");
        }
    }

    /** Só a cliente do atendimento pode cancelá-lo ou reagendá-lo como cliente. */
    private static void validarClienteDoAgendamento(Agendamento agendamento, UUID clienteId) {
        if (!agendamento.getCliente().getId().equals(clienteId)) {
            throw new RegraDeNegocioException("Você não participou deste atendimento");
        }
    }

    /** O id da rota precisa ser o do usuário logado. */
    private static void exigirProprioUsuario(UUID idDaRota, UUID usuarioId, String mensagem) {
        if (!idDaRota.equals(usuarioId)) {
            throw new RegraDeNegocioException(mensagem);
        }
    }

    public AgendamentoResponse reagendarPeloCliente(UUID agendamentoId, UUID clienteId,
                                                    ReagendamentoRequest request) {
        Agendamento agendamento = buscarAgendamento(agendamentoId);
        validarClienteDoAgendamento(agendamento, clienteId);
        // Mesmo prazo do cancelamento: sem isso, remarcar em cima da hora liberaria a vaga sem antecedência
        if (agendamento.getDataHoraInicio().isBefore(LocalDateTime.now(KiraTimeZone.DEFAULT)
                .plusHours(CancelamentoClienteStrategy.ANTECEDENCIA_MINIMA_HORAS))) {
            throw new RegraDeNegocioException("Remarcação pelo cliente deve ser feita com no mínimo "
                    + CancelamentoClienteStrategy.ANTECEDENCIA_MINIMA_HORAS + " horas de antecedência");
        }
        return reagendar(agendamento, request);
    }

    public AgendamentoResponse reagendarPelaEmpresa(UUID agendamentoId, UUID empresaId,
                                                    ReagendamentoRequest request) {
        Agendamento agendamento = buscarAgendamento(agendamentoId);
        validarEmpresaDoAgendamento(agendamento, empresaId);
        return reagendar(agendamento, request);
    }

    private Agendamento buscarAgendamento(UUID agendamentoId) {
        return agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));
    }

    private AgendamentoResponse reagendar(Agendamento agendamento, ReagendamentoRequest request) {
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO ||
                agendamento.getStatus() == StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Não é possível reagendar este agendamento");
        }

        // Cancelar continua permitido: a cliente precisa conseguir desmarcar
        if (!agendamento.getEmpresa().isEnabled()) {
            throw new RegraDeNegocioException("Este estabelecimento não está mais disponível");
        }

        LocalDateTime novaDataHoraFim = request.novaDataHoraInicio()
                .plusMinutes(agendamento.getServico().getDuracaoMinutos());

        validarDataFutura(request.novaDataHoraInicio());
        validarExpediente(agendamento.getEmpresa().getId(), request.novaDataHoraInicio(), novaDataHoraFim);
        // Mantém a funcionária; se ela foi desativada, o horário fica com outra que esteja livre
        Funcionaria atual = agendamento.getFuncionaria();
        Funcionaria escolhida = atual != null && !Boolean.FALSE.equals(atual.getAtivo()) ? atual : null;
        Funcionaria quemAtende = reservarHorario(agendamento.getEmpresa().getId(), escolhida,
                request.novaDataHoraInicio(), novaDataHoraFim, agendamento.getId());

        agendamento.setFuncionaria(quemAtende);
        agendamento.setDataHoraInicio(request.novaDataHoraInicio());
        agendamento.setDataHoraFim(novaDataHoraFim);
        agendamento.setStatus(StatusAgendamento.REAGENDADO);
        agendamento.setLembreteEnviado(false);

        return toResponse(agendamentoRepository.save(agendamento));
    }
    public List<AgendamentoResponse> historicoCliente(UUID clienteId, UUID usuarioId) {
        exigirProprioUsuario(clienteId, usuarioId, SO_OS_SEUS_AGENDAMENTOS);
        return agendamentoRepository
                .findByClienteIdAndStatusOrderByDataHoraInicioDesc(
                        clienteId, StatusAgendamento.CONCLUIDO)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validarDataFutura(LocalDateTime inicio) {
        if (!inicio.isAfter(LocalDateTime.now(KiraTimeZone.DEFAULT))) {
            throw new RegraDeNegocioException("Não é possível agendar para uma data no passado");
        }
    }

    /**
     * O atendimento precisa começar e terminar dentro do expediente cadastrado
     * pelo estabelecimento para o dia da semana.
     */
    private void validarExpediente(UUID empresaId, LocalDateTime inicio, LocalDateTime fim) {
        DiaSemana diaSemana = DiaSemana.values()[inicio.getDayOfWeek().getValue() % 7];

        DisponibilidadeEstudio disponibilidade = disponibilidadeRepository
                .findByEmpresaIdAndDiaSemana(empresaId, diaSemana)
                .orElseThrow(() -> new RegraDeNegocioException("Empresa não atende neste dia da semana"));

        LocalDateTime inicioExpediente = LocalDateTime.of(inicio.toLocalDate(), disponibilidade.getHoraInicio());
        LocalDateTime fimExpediente = LocalDateTime.of(inicio.toLocalDate(), disponibilidade.getHoraFim());

        if (inicio.isBefore(inicioExpediente) || fim.isAfter(fimExpediente)) {
            throw new RegraDeNegocioException("Horário fora do expediente do estabelecimento");
        }
    }

    /**
     * Confere se o horário está livre e devolve quem atende. Estúdio sem funcionárias atende
     * um cliente por vez. Com funcionárias, a escolhida precisa estar livre; sem escolha, o
     * horário fica com a livre que tem menos atendimentos no dia. Agendamentos cancelados
     * liberam o horário e, no reagendamento, o próprio agendamento é desconsiderado.
     */
    private Funcionaria reservarHorario(UUID empresaId, Funcionaria escolhida, LocalDateTime inicio,
                                        LocalDateTime fim, UUID agendamentoIgnorado) {
        // Carrega o dia inteiro para contar quantos atendimentos cada funcionária já tem
        QuadroDeHorarios quadro = ocupacaoService.carregar(empresaId, inicio.toLocalDate().atStartOfDay(),
                fim.toLocalDate().plusDays(1).atStartOfDay(), agendamentoIgnorado);

        if (!quadro.temFuncionarias()) {
            if (!quadro.livre(null, inicio, fim)) {
                throw new RegraDeNegocioException(
                        "Já existe um agendamento neste horário para este estabelecimento");
            }
            return escolhida;
        }

        UUID escolhidaId = escolhida != null ? escolhida.getId() : null;
        return quadro.funcionariaLivre(escolhidaId, inicio, fim)
                .orElseThrow(() -> new RegraDeNegocioException(escolhida != null
                        ? "A funcionária não está livre neste horário"
                        : "Já existe um agendamento neste horário para este estabelecimento"));
    }

    private AgendamentoResponse toResponse(Agendamento a) {
        return AgendamentoResponse.from(a);
    }
}
