package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.dto.request.ServicoRequest;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ServicoService {

    @Autowired
    private ServicoRepository servicoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public ServicoResponse cadastrar(UUID empresaId, ServicoRequest request) {
        Empresa empresa = (Empresa) usuarioRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada"));

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

    public ServicoResponse atualizar(UUID id, ServicoRequest request) {
        Servico servico = servicoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serviço não encontrado"));

        servico.setNome(request.nome());
        servico.setDescricao(request.descricao());
        servico.setDuracaoMinutos(request.duracaoMinutos());
        servico.setPreco(request.preco());

        Servico salvo = servicoRepository.save(servico);
        return toResponse(salvo);
    }

    public void desativar(UUID id) {
        Servico servico = servicoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serviço não encontrado"));

        servico.setAtivo(false);
        servicoRepository.save(servico);
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