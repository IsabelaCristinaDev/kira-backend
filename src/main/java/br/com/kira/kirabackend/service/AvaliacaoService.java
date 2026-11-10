package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Agendamento;
import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import br.com.kira.kirabackend.dto.request.AvaliacaoRequest;
import br.com.kira.kirabackend.dto.response.AvaliacaoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.AcessoNegadoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
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
    private final UsuarioRepository usuarioRepository;

    public AvaliacaoResponse avaliarComoCliente(UUID clienteId, AvaliacaoRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(request.agendamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));

        if (!agendamento.getCliente().getId().equals(clienteId)) {
            throw new AcessoNegadoException("Você não participou deste atendimento");
        }

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

    public AvaliacaoResponse avaliarComoEmpresa(UUID empresaId, AvaliacaoRequest request) {
        Agendamento agendamento = agendamentoRepository.findById(request.agendamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(AGENDAMENTO_NAO_ENCONTRADO));

        if (!agendamento.getEmpresa().getId().equals(empresaId)) {
            throw new AcessoNegadoException("Este atendimento não pertence ao seu estabelecimento");
        }

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
        exigirEmpresaAtiva(empresaId);
        return avaliacaoRepository
                .findByEmpresaIdAndTipoAvaliador(empresaId, TipoAvaliador.CLIENTE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AvaliacaoResponse> listarAvaliacoesCliente(UUID clienteId, UUID usuarioId) {
        if (!clienteId.equals(usuarioId)) {
            throw new AcessoNegadoException("Você só pode ver as suas avaliações");
        }
        return avaliacaoRepository
                .findByClienteIdAndTipoAvaliador(clienteId, TipoAvaliador.EMPRESA)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * As avaliações que a conta logada fez: a cliente, as dos estúdios; o estúdio, as das clientes.
     * Com elas, a tela sabe quais atendimentos já avaliou. As recebidas têm rotas próprias.
     */
    public List<AvaliacaoResponse> listarMinhasAvaliacoes(Usuario usuario) {
        List<Avaliacao> feitas = usuario instanceof Empresa
                ? avaliacaoRepository.findByEmpresaIdAndTipoAvaliador(usuario.getId(), TipoAvaliador.EMPRESA)
                : avaliacaoRepository.findByClienteIdAndTipoAvaliador(usuario.getId(), TipoAvaliador.CLIENTE);
        return feitas.stream().map(this::toResponse).toList();
    }

    public Double mediaAvaliacaoEmpresa(UUID empresaId) {
        exigirEmpresaAtiva(empresaId);
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

    // Empresa desativada responde como inexistente, igual à vitrine
    private void exigirEmpresaAtiva(UUID empresaId) {
        if (!usuarioRepository.existsEmpresaAtivaById(empresaId)) {
            throw new RecursoNaoEncontradoException("Empresa não encontrada");
        }
    }
}