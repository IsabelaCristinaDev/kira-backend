package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Denuncia;
import br.com.kira.kirabackend.domain.entity.SuspensaoPerfil;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.StatusDenuncia;
import br.com.kira.kirabackend.dto.request.AnaliseDenunciaRequest;
import br.com.kira.kirabackend.dto.request.DenunciaRequest;
import br.com.kira.kirabackend.dto.response.DenunciaRegistradaResponse;
import br.com.kira.kirabackend.dto.response.DenunciaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.observer.DenunciaRiscoImediatoEvent;
import br.com.kira.kirabackend.observer.PerfilSuspensoPreventivamenteEvent;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.DenunciaRepository;
import br.com.kira.kirabackend.repository.SuspensaoPerfilRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional
public class DenunciaService {

    static final String TELEFONE_EMERGENCIA = "190";
    static final String ORIENTACAO_RISCO_IMEDIATO_COM_SUSPENSAO =
            "Ligue 190 agora. A equipe Kira já foi notificada e o perfil denunciado foi suspenso preventivamente.";
    static final String ORIENTACAO_RISCO_IMEDIATO_SEM_SUSPENSAO =
            "Ligue 190 agora. A equipe Kira já foi notificada e vai analisar a denúncia com prioridade.";
    // Folga em volta do horário do agendamento para considerar o atendimento "em andamento".
    static final Duration JANELA_ATENDIMENTO = Duration.ofHours(2);
    static final String ORIENTACAO_PADRAO =
            "Denúncia registrada de forma anônima. A equipe Kira vai analisar e decidir o encaminhamento.";

    private final DenunciaRepository denunciaRepository;
    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final SuspensaoPerfilRepository suspensaoPerfilRepository;
    private final SuspensaoPerfilService suspensaoPerfilService;
    private final ApplicationEventPublisher eventPublisher;

    public DenunciaRegistradaResponse registrar(UUID denuncianteId, DenunciaRequest request) {
        if (denuncianteId.equals(request.denunciadoId())) {
            throw new RegraDeNegocioException("Você não pode denunciar o seu próprio perfil");
        }

        Usuario denunciante = usuarioRepository.findById(denuncianteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
        Usuario denunciado = usuarioRepository.findById(request.denunciadoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário denunciado não encontrado"));
        if (denunciado instanceof Admin) {
            throw new RegraDeNegocioException("Não é possível denunciar um perfil da equipe Kira");
        }

        boolean riscoImediato = Boolean.TRUE.equals(request.riscoImediato());
        Agendamento agendamento = buscarAgendamento(request, denuncianteId);

        if (denunciaRepository.existsByDenuncianteIdAndDenunciadoIdAndStatus(
                denuncianteId, denunciado.getId(), StatusDenuncia.PENDENTE)) {
            throw new RegraDeNegocioException("Você já possui uma denúncia em análise contra este perfil");
        }

        Denuncia denuncia = new Denuncia();
        denuncia.setDenunciante(denunciante);
        denuncia.setDenunciado(denunciado);
        denuncia.setAgendamento(agendamento);
        denuncia.setMotivo(request.motivo());
        denuncia.setDescricao(request.descricao());
        denuncia.setRiscoImediato(riscoImediato);
        // No risco imediato a equipe precisa saber quem denunciou para o contato rápido.
        denuncia.setAnonima(!riscoImediato);
        denuncia.setStatus(StatusDenuncia.PENDENTE);
        denuncia.setAutoridadeAcionada(false);
        denuncia.setInfundada(false);

        Denuncia salva = denunciaRepository.save(denuncia);

        boolean suspendeNaHora = riscoImediato && podeSuspenderNaHora(denuncianteId, agendamento);
        if (suspendeNaHora) {
            suspensaoPerfilService.suspender(denunciado, salva, true);
            eventPublisher.publishEvent(new PerfilSuspensoPreventivamenteEvent(
                    denunciado.getEmail(), denunciado.getNome()));
        }
        if (riscoImediato) {
            eventPublisher.publishEvent(new DenunciaRiscoImediatoEvent(
                    salva.getId(),
                    salva.getMotivo(),
                    denunciante.getNome(),
                    denunciante.getTelefone(),
                    denunciado.getNome(),
                    suspendeNaHora,
                    salva.getDataCriacao()));
        }

        return new DenunciaRegistradaResponse(
                salva.getId(),
                salva.getStatus(),
                riscoImediato,
                suspendeNaHora,
                riscoImediato ? TELEFONE_EMERGENCIA : null,
                orientacao(riscoImediato, suspendeNaHora),
                salva.getDataCriacao());
    }

    // A suspensão automática acontece antes de qualquer análise humana, então só vale
    // para perigo durante um atendimento real em andamento e para quem nunca teve
    // uma denúncia de risco imediato julgada infundada.
    private boolean podeSuspenderNaHora(UUID denuncianteId, Agendamento agendamento) {
        if (agendamento == null) {
            return false;
        }
        StatusAgendamento status = agendamento.getStatus();
        if (status == StatusAgendamento.CANCELADO || status == StatusAgendamento.REAGENDADO) {
            return false;
        }
        LocalDateTime agora = LocalDateTime.now(KiraTimeZone.DEFAULT);
        boolean emAndamento = !agora.isBefore(agendamento.getDataHoraInicio().minus(JANELA_ATENDIMENTO))
                && !agora.isAfter(agendamento.getDataHoraFim().plus(JANELA_ATENDIMENTO));
        if (!emAndamento) {
            return false;
        }
        return !denunciaRepository.existsByDenuncianteIdAndRiscoImediatoTrueAndInfundadaTrue(denuncianteId);
    }

    private String orientacao(boolean riscoImediato, boolean perfilSuspenso) {
        if (!riscoImediato) {
            return ORIENTACAO_PADRAO;
        }
        return perfilSuspenso ? ORIENTACAO_RISCO_IMEDIATO_COM_SUSPENSAO : ORIENTACAO_RISCO_IMEDIATO_SEM_SUSPENSAO;
    }

    @Transactional(readOnly = true)
    public List<DenunciaResponse> listar(StatusDenuncia status) {
        return denunciaRepository
                .findByStatusOrderByRiscoImediatoDescDataCriacaoAsc(status)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DenunciaResponse buscar(UUID id) {
        return toResponse(buscarDenuncia(id));
    }

    public DenunciaResponse analisar(UUID id, UUID adminId, AnaliseDenunciaRequest request) {
        Denuncia denuncia = buscarDenuncia(id);
        if (denuncia.getStatus() != StatusDenuncia.PENDENTE) {
            throw new RegraDeNegocioException("Esta denúncia já foi analisada");
        }
        if (request.decisao() == StatusDenuncia.PENDENTE) {
            throw new RegraDeNegocioException("A decisão deve ser ENCAMINHADA ou ARQUIVADA");
        }
        boolean suspender = Boolean.TRUE.equals(request.suspenderPerfil());
        if (request.decisao() == StatusDenuncia.ARQUIVADA && suspender) {
            throw new RegraDeNegocioException("Uma denúncia arquivada não pode manter o perfil suspenso");
        }
        boolean infundada = Boolean.TRUE.equals(request.infundada());
        if (infundada && request.decisao() != StatusDenuncia.ARQUIVADA) {
            throw new RegraDeNegocioException("Só uma denúncia arquivada pode ser marcada como infundada");
        }

        denuncia.setStatus(request.decisao());
        denuncia.setParecer(request.parecer());
        denuncia.setAutoridadeAcionada(Boolean.TRUE.equals(request.autoridadeAcionada()));
        denuncia.setInfundada(infundada);
        denuncia.setAnalisadaPor(usuarioRepository.getReferenceById(adminId));
        denuncia.setDataAnalise(LocalDateTime.now(KiraTimeZone.DEFAULT));
        Denuncia salva = denunciaRepository.save(denuncia);

        if (!suspender) {
            suspensaoPerfilService.revogarDaDenuncia(id, adminId);
        } else if (suspensaoPerfilRepository.existsByDenunciaIdAndAtivaTrue(id)) {
            suspensaoPerfilService.confirmarDaDenuncia(id);
        } else {
            suspensaoPerfilService.suspender(denuncia.getDenunciado(), salva, false);
        }

        return toResponse(salva);
    }

    private Agendamento buscarAgendamento(DenunciaRequest request, UUID denuncianteId) {
        if (request.agendamentoId() == null) {
            return null;
        }
        Agendamento agendamento = agendamentoRepository.findById(request.agendamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));

        UUID clienteId = agendamento.getCliente().getId();
        UUID empresaId = agendamento.getEmpresa().getId();
        boolean envolveAsDuasPartes =
                (denuncianteId.equals(clienteId) && request.denunciadoId().equals(empresaId))
                        || (denuncianteId.equals(empresaId) && request.denunciadoId().equals(clienteId));
        if (!envolveAsDuasPartes) {
            throw new RegraDeNegocioException("O agendamento informado não envolve você e o perfil denunciado");
        }
        return agendamento;
    }

    private Denuncia buscarDenuncia(UUID id) {
        return denunciaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Denúncia não encontrada"));
    }

    private DenunciaResponse toResponse(Denuncia d) {
        boolean anonima = Boolean.TRUE.equals(d.getAnonima());
        Usuario denunciante = d.getDenunciante();
        UUID suspensaoAtivaId = suspensaoPerfilRepository.findByDenunciaIdAndAtivaTrue(d.getId())
                .stream()
                .map(SuspensaoPerfil::getId)
                .findFirst()
                .orElse(null);
        return new DenunciaResponse(
                d.getId(),
                d.getDenunciado().getId(),
                d.getDenunciado().getNome(),
                anonima ? null : denunciante.getId(),
                anonima ? null : denunciante.getNome(),
                anonima ? null : denunciante.getTelefone(),
                d.getAgendamento() != null ? d.getAgendamento().getId() : null,
                d.getMotivo(),
                d.getDescricao(),
                d.getRiscoImediato(),
                anonima,
                d.getStatus(),
                suspensaoAtivaId != null,
                suspensaoAtivaId,
                d.getAutoridadeAcionada(),
                d.getInfundada(),
                d.getParecer(),
                d.getDataCriacao(),
                d.getDataAnalise()
        );
    }
}
