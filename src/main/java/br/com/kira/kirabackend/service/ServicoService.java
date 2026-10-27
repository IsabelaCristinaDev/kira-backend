package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.dto.request.ServicoRequest;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ServicoService {

    private final ServicoRepository servicoRepository;
    private final UsuarioRepository usuarioRepository;

    public ServicoResponse cadastrar(UUID empresaId, ServicoRequest request) {
        Empresa empresa = (Empresa) usuarioRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        Servico servico = new Servico();
        servico.setNome(request.nome());
        servico.setDescricao(request.descricao());
        servico.setDuracaoMinutos(request.duracaoMinutos());
        servico.setPreco(request.preco());
        servico.setAtivo(true);
        servico.setEmpresa(empresa);

        Servico salvo = servicoRepository.save(servico);
        return toResponse(salvo);
    }

    public List<ServicoResponse> listarPorEmpresa(UUID empresaId) {
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

        Servico salvo = servicoRepository.save(servico);
        return toResponse(salvo);
    }

    public void desativar(UUID empresaId, UUID id) {
        Servico servico = buscarDaEmpresa(empresaId, id);

        servico.setAtivo(false);
        servicoRepository.save(servico);
    }

    /** Serviço de outra empresa é tratado como inexistente na rota desta empresa. */
    private Servico buscarDaEmpresa(UUID empresaId, UUID id) {
        return servicoRepository.findById(id)
                .filter(s -> s.getEmpresa() != null && empresaId.equals(s.getEmpresa().getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));
    }

    private ServicoResponse toResponse(Servico servico) {
        return new ServicoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getDescricao(),
                servico.getDuracaoMinutos(),
                servico.getPreco(),
                servico.getAtivo()
        );
    }
}