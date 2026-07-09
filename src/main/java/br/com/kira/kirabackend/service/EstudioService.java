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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class EstudioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AvaliacaoRepository avaliacaoRepository;

    @Autowired
    private FuncionariaRepository funcionariaRepository;

    @Autowired
    private ServicoRepository servicoRepository;

    public List<EstudioResponse> listarTodos() {
        return usuarioRepository.findAll()
                .stream()
                .filter(u -> u instanceof Empresa)
                .map(u -> toResponse((Empresa) u))
                .toList();
    }

    public List<EstudioResponse> buscarPorNome(String nome) {
        return usuarioRepository.findAll()
                .stream()
                .filter(u -> u instanceof Empresa)
                .map(u -> (Empresa) u)
                .filter(e -> e.getNome().toLowerCase()
                        .contains(nome.toLowerCase()))
                .map(this::toResponse)
                .toList();
    }

    public EstudioResponse buscarPorId(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estúdio não encontrado"));

        if (!(usuario instanceof Empresa)) {
            throw new RecursoNaoEncontradoException("Estúdio não encontrado");
        }

        Empresa empresa = (Empresa) usuario;
        return toResponse(empresa);
    }

    public List<EstudioResponse> buscarPorTipo(TipoEstabelecimento tipo) {
        return usuarioRepository.findAll()
                .stream()
                .filter(u -> u instanceof Empresa)
                .map(u -> (Empresa) u)
                .filter(e -> e.getTipoEstabelecimento() == tipo)
                .map(this::toResponse)
                .toList();
    }

    public List<EstudioResponse> buscarPorCidade(String cidade) {
        return usuarioRepository.findAll()
                .stream()
                .filter(u -> u instanceof Empresa)
                .map(u -> (Empresa) u)
                .filter(e -> e.getEndereco() != null
                        && e.getEndereco().getCidade() != null
                        && e.getEndereco().getCidade().toLowerCase()
                        .contains(cidade.toLowerCase()))
                .map(this::toResponse)
                .toList();
    }

    public List<FuncionariaResponse> listarFuncionarias(UUID empresaId) {
        return funcionariaRepository.findByEmpresaIdAndAtivoTrue(empresaId)
                .stream()
                .map(f -> new FuncionariaResponse(
                        f.getId(),
                        f.getNome(),
                        f.getFotoUrl(),
                        f.getEspecialidades(),
                        f.getAtivo()))
                .toList();
    }

    public List<ServicoResponse> listarServicos(UUID empresaId) {
        return servicoRepository.findByEmpresaIdAndAtivoTrue(empresaId)
                .stream()
                .map(s -> new ServicoResponse(
                        s.getId(),
                        s.getNome(),
                        s.getDescricao(),
                        s.getDuracaoMinutos(),
                        s.getPreco(),
                        s.getAtivo()))
                .toList();
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
                media
        );
    }
}