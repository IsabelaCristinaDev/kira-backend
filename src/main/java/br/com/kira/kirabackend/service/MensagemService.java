package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Mensagem;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.MensagemRequest;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
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


        Mensagem mensagem = new Mensagem();
        mensagem.setAgendamento(agendamento);
        mensagem.setRemetente(remetente);
        mensagem.setConteudo(request.conteudo());

        Mensagem salva = mensagemRepository.save(mensagem);
        return toResponse(salva);
    }

    public List<MensagemResponse> listarConversa(UUID agendamentoId) {
        return mensagemRepository
                .findByAgendamentoIdOrderByDataEnvioAsc(agendamentoId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void marcarComoLida(UUID mensagemId) {
        Mensagem mensagem = mensagemRepository.findById(mensagemId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensagem não encontrada"));
        mensagem.setLida(true);
        mensagemRepository.save(mensagem);
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