package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import br.com.kira.kirabackend.dto.request.AvaliacaoRequest;
import br.com.kira.kirabackend.dto.response.AvaliacaoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import jakarta.transaction.Transactional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class AvaliacaoService {

    private static final String AGENDAMENTO_NAO_ENCONTRADO = "Agendamento não encontrado";

    private final AvaliacaoRepository avaliacaoRepository;
    private final AgendamentoRepository agendamentoRepository;

    public AvaliacaoResponse avaliarComoCliente(AvaliacaoRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(request.agendamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));

        if (agendamento.getStatus() != StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Só é possível avaliar agendamentos concluídos");
        }

        if (avaliacaoRepository.existsByAgendamentoIdAndTipoAvaliador(
                request.agendamentoId(), TipoAvaliador.CLIENTE)) {
            throw new RegraDeNegocioException("Você já avaliou este atendimento");
        }

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(agendamento);
        avaliacao.setCliente(agendamento.getCliente());
        avaliacao.setEmpresa(agendamento.getEmpresa());
        avaliacao.setTipoAvaliador(TipoAvaliador.CLIENTE);
        avaliacao.setNota(request.nota());
        avaliacao.setComentario(request.comentario());

        return toResponse(avaliacaoRepository.save(avaliacao));
    }

    public AvaliacaoResponse avaliarComoEmpresa(AvaliacaoRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(request.agendamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));

        if (agendamento.getStatus() != StatusAgendamento.CONCLUIDO) {
            throw new RegraDeNegocioException("Só é possível avaliar agendamentos concluídos");
        }

        if (avaliacaoRepository.existsByAgendamentoIdAndTipoAvaliador(
                request.agendamentoId(), TipoAvaliador.EMPRESA)) {
            throw new RegraDeNegocioException("Este atendimento já foi avaliado");
        }

        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setAgendamento(agendamento);
        avaliacao.setCliente(agendamento.getCliente());
        avaliacao.setEmpresa(agendamento.getEmpresa());
        avaliacao.setTipoAvaliador(TipoAvaliador.EMPRESA);
        avaliacao.setNota(request.nota());
        avaliacao.setComentario(request.comentario());

        return toResponse(avaliacaoRepository.save(avaliacao));
    }

    public List<AvaliacaoResponse> listarAvaliacoesEmpresa(UUID empresaId) {
        return avaliacaoRepository
                .findByEmpresaIdAndTipoAvaliador(empresaId, TipoAvaliador.CLIENTE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AvaliacaoResponse> listarAvaliacoesCliente(UUID clienteId) {
        return avaliacaoRepository
                .findByClienteIdAndTipoAvaliador(clienteId, TipoAvaliador.EMPRESA)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Double mediaAvaliacaoEmpresa(UUID empresaId) {
        return avaliacaoRepository.calcularMediaAvaliacaoEmpresa(empresaId);
    }

    private AvaliacaoResponse toResponse(Avaliacao a) {
        return new AvaliacaoResponse(
                a.getId(),
                a.getAgendamento().getId(),
                a.getCliente().getId(),
                a.getCliente().getNome(),
                a.getEmpresa().getId(),
                a.getEmpresa().getNome(),
                a.getTipoAvaliador(),
                a.getNota(),
                a.getComentario(),
                a.getDataCriacao()
        );
    }
}