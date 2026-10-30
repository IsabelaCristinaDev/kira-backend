package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.response.EnderecoResponse;
import br.com.kira.kirabackend.dto.response.EstudioResponse;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
@RequiredArgsConstructor
@Service
@Transactional
public class EstudioService {

    private final UsuarioRepository usuarioRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final FuncionariaRepository funcionariaRepository;
    private final ServicoRepository servicoRepository;

    public List<EstudioResponse> listarTodos() {
        return empresasAtivas()
                .map(this::toResponse)
                .toList();
    }

    public List<EstudioResponse> buscarPorNome(String nome) {
        return empresasAtivas()
                .filter(e -> e.getNome().toLowerCase()
                        .contains(nome.toLowerCase()))
                .map(this::toResponse)
                .toList();
    }

    public EstudioResponse buscarPorId(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estúdio não encontrado"));

        // Empresa desativada responde como inexistente, para não aparecer na vitrine
        if (!(usuario instanceof Empresa empresa) || !empresa.isEnabled()) {
            throw new RecursoNaoEncontradoException("Estúdio não encontrado");
        }

        return toResponse(empresa);
    }

    public List<EstudioResponse> buscarPorTipo(TipoEstabelecimento tipo) {
        return empresasAtivas()
                .filter(e -> e.getTipoEstabelecimento() == tipo)
                .map(this::toResponse)
                .toList();
    }

    public List<EstudioResponse> buscarPorCidade(String cidade) {
        return empresasAtivas()
                .filter(e -> e.getEndereco() != null
                        && e.getEndereco().getCidade() != null
                        && e.getEndereco().getCidade().toLowerCase()
                        .contains(cidade.toLowerCase()))
                .map(this::toResponse)
                .toList();
    }

    public List<FuncionariaResponse> listarFuncionarias(UUID empresaId) {
        exigirEmpresaAtiva(empresaId);
        return funcionariaRepository.findByEmpresaIdAndAtivoTrue(empresaId)
                .stream()
                .map(f -> new FuncionariaResponse(
                        f.getId(),
                        f.getNome(),
                        f.getFotoUrl(),
                        f.getEspecialidades(),
                        f.getAtivo(),
                        f.getComissaoPercentual()))
                .toList();
    }

    public List<ServicoResponse> listarServicos(UUID empresaId) {
        exigirEmpresaAtiva(empresaId);
        return servicoRepository.findByEmpresaIdAndAtivoTrue(empresaId)
                .stream()
                .map(ServicoResponse::from)
                .toList();
    }

    private Stream<Empresa> empresasAtivas() {
        return usuarioRepository.findAll()
                .stream()
                .filter(Empresa.class::isInstance)
                .map(Empresa.class::cast)
                .filter(Empresa::isEnabled);
    }

    private EstudioResponse toResponse(Empresa empresa) {
        Double media = avaliacaoRepository
                .calcularMediaAvaliacaoEmpresa(empresa.getId());
        return new EstudioResponse(
                empresa.getId(),
                empresa.getNome(),
                empresa.getDescricao(),
                EnderecoResponse.from(empresa.getEndereco()),
                empresa.getTelefone(),
                empresa.getFotoUrl(),
                empresa.getTipoEstabelecimento(),
                media,
                empresa.getLatitude(),
                empresa.getLongitude()
        );
    }

    // Empresa desativada responde como inexistente, igual à vitrine
    private void exigirEmpresaAtiva(UUID empresaId) {
        if (!usuarioRepository.existsEmpresaAtivaById(empresaId)) {
            throw new RecursoNaoEncontradoException("Estúdio não encontrado");
        }
    }
}