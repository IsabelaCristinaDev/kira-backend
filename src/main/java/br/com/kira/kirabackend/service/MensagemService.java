package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Mensagem;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.request.MensagemRequest;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.MensagemRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Transactional
public class MensagemService {

    private final MensagemRepository mensagemRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final UsuarioRepository usuarioRepository;

    public MensagemResponse enviar(UUID remetenteId, MensagemRequest request) {
        Agendamento agendamento = agendamentoRepository
                .findById(request.agendamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));

        Usuario remetente = usuarioRepository.findById(remetenteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        validarParticipante(agendamento, remetenteId);
        // A conversa continua aberta depois de concluído (ex.: objeto esquecido); no cancelado, é só leitura
        if (agendamento.getStatus() == StatusAgendamento.CANCELADO) {
            throw new RegraDeNegocioException("Este agendamento foi cancelado: a conversa está só para leitura");
        }

        Mensagem mensagem = new Mensagem();
        mensagem.setAgendamento(agendamento);
        mensagem.setRemetente(remetente);
        mensagem.setConteudo(request.conteudo());

        Mensagem salva = mensagemRepository.save(mensagem);
        return toResponse(salva);
    }

    public List<MensagemResponse> listarConversa(UUID agendamentoId, UUID usuarioId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));
        validarParticipante(agendamento, usuarioId);

        return mensagemRepository
                .findByAgendamentoIdOrderByDataEnvioAsc(agendamentoId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void marcarComoLida(UUID mensagemId, UUID usuarioId) {
        Mensagem mensagem = mensagemRepository.findById(mensagemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensagem não encontrada"));
        validarParticipante(mensagem.getAgendamento(), usuarioId);
        if (mensagem.getRemetente().getId().equals(usuarioId)) {
            throw new RegraDeNegocioException("Só quem recebeu a mensagem pode marcá-la como lida");
        }
        mensagem.setLida(true);
        mensagemRepository.save(mensagem);
    }

    /** Marca de uma vez o que a pessoa recebeu na conversa; as mensagens que ela enviou não mudam. */
    public void marcarConversaComoLida(UUID agendamentoId, UUID usuarioId) {
        Agendamento agendamento = agendamentoRepository.findById(agendamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Agendamento não encontrado"));
        validarParticipante(agendamento, usuarioId);
        mensagemRepository.marcarRecebidasComoLidas(agendamentoId, usuarioId);
    }

    /** Só a cliente e a empresa do agendamento podem ver e usar a conversa. */
    private static void validarParticipante(Agendamento agendamento, UUID usuarioId) {
        boolean participa = usuarioId.equals(agendamento.getCliente().getId())
                || usuarioId.equals(agendamento.getEmpresa().getId());
        if (!participa) {
            throw new RegraDeNegocioException("Você não participa deste agendamento");
        }
    }

    private MensagemResponse toResponse(Mensagem m) {
        return new MensagemResponse(
                m.getId(),
                m.getAgendamento().getId(),
                m.getRemetente().getId(),
                m.getRemetente().getNome(),
                m.getConteudo(),
                m.getLida(),
                m.getDataEnvio()
        );
    }
}
