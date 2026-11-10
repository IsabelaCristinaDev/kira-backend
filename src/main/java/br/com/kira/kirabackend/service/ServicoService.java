package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.dto.request.ServicoRequest;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ServicoService {

    private final ServicoRepository servicoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ImagensEnviadas imagens;

    public ServicoResponse cadastrar(UUID empresaId, ServicoRequest request) {
        Empresa empresa = (Empresa) usuarioRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        Servico servico = new Servico();
        servico.setNome(request.nome());
        servico.setDescricao(request.descricao());
        servico.setDuracaoMinutos(request.duracaoMinutos());
        servico.setPreco(request.preco());
        servico.setCategoria(CategoriaServico.escolhidaOuPeloNome(request.categoria(), request.nome()));
        servico.setAtivo(true);
        servico.setEmpresa(empresa);

        Servico salvo = servicoRepository.save(servico);
        return toResponse(salvo);
    }

    public List<ServicoResponse> listarPorEmpresa(UUID empresaId) {
        exigirEmpresaAtiva(empresaId);
        return servicoRepository.findByEmpresaIdAndAtivoTrue(empresaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ServicoResponse atualizar(UUID empresaId, UUID id, ServicoRequest request) {
        Servico servico = buscarDaEmpresa(empresaId, id);
        servico.setNome(request.nome());
        servico.setDescricao(request.descricao());
        servico.setDuracaoMinutos(request.duracaoMinutos());
        servico.setPreco(request.preco());
        servico.setCategoria(CategoriaServico.escolhidaOuPeloNome(request.categoria(), request.nome()));

        Servico salvo = servicoRepository.save(servico);
        return toResponse(salvo);
    }

    public void desativar(UUID empresaId, UUID id) {
        Servico servico = buscarDaEmpresa(empresaId, id);

        servico.setAtivo(false);
        servicoRepository.save(servico);
    }

    /** Troca a foto do serviço. A anterior só é apagada depois que a nova foi gravada. */
    public ServicoResponse definirFoto(UUID empresaId, UUID id, MultipartFile arquivo) {
        Servico servico = buscarDaEmpresa(empresaId, id);
        String fotoAnterior = servico.getFotoUrl();
        String fotoNova = imagens.guardar(arquivo);
        servico.setFotoUrl(fotoNova);
        Servico salvo;
        try {
            salvo = servicoRepository.save(servico);
        } catch (RuntimeException e) {
            imagens.apagarSemFalhar(fotoNova);
            throw e;
        }
        imagens.apagarSemFalhar(fotoAnterior);
        return toResponse(salvo);
    }

    public void removerFoto(UUID empresaId, UUID id) {
        Servico servico = buscarDaEmpresa(empresaId, id);
        String fotoAnterior = servico.getFotoUrl();
        servico.setFotoUrl(null);
        servicoRepository.save(servico);
        imagens.apagarSemFalhar(fotoAnterior);
    }

    /** Serviço de outra empresa é tratado como inexistente na rota desta empresa. */
    private Servico buscarDaEmpresa(UUID empresaId, UUID id) {
        return servicoRepository.findById(id)
                .filter(s -> s.getEmpresa() != null && empresaId.equals(s.getEmpresa().getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
    }

    private ServicoResponse toResponse(Servico servico) {
        return ServicoResponse.from(servico);
    }

    // Empresa desativada responde como inexistente, igual à vitrine
    private void exigirEmpresaAtiva(UUID empresaId) {
        if (!usuarioRepository.existsEmpresaAtivaById(empresaId)) {
            throw new RecursoNaoEncontradoException("Empresa não encontrada");
        }
    }
}