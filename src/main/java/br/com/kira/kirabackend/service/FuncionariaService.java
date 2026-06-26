package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.dto.request.FuncionariaRequest;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FuncionariaService {

    @Autowired
    private FuncionariaRepository funcionariaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public FuncionariaResponse cadastrar(UUID empresaId, FuncionariaRequest request) {
        Empresa empresa = (Empresa) usuarioRepository.findById(empresaId)
                .orElseThrow(() -> new RuntimeException("Empresa não encontrada"));

        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setNome(request.nome());
        funcionaria.setFotoUrl(request.fotoUrl());
        funcionaria.setEspecialidades(request.especialidade());
        funcionaria.setEmpresa(empresa);

        funcionariaRepository.save(funcionaria);

        return toResponse(funcionaria);
    }

    public List<FuncionariaResponse> listarPorEmpresa(UUID empresaId) {
        return funcionariaRepository.findByEmpresaIdAndAtivoTrue(empresaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public FuncionariaResponse atualizar(UUID id, FuncionariaRequest request) {
        Funcionaria funcionaria = funcionariaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Funcionária não encontrada"));

        funcionaria.setNome(request.nome());
        funcionaria.setFotoUrl(request.fotoUrl());
        funcionaria.setEspecialidades(request.especialidade());

        funcionariaRepository.save(funcionaria);

        return toResponse(funcionaria);
    }

    public void desativar(UUID id) {
        Funcionaria funcionaria = funcionariaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Funcionária não encontrada"));

        funcionaria.setAtivo(false);
        funcionariaRepository.save(funcionaria);
    }

    private FuncionariaResponse toResponse(Funcionaria funcionaria) {
        return new FuncionariaResponse(
                funcionaria.getId(),
                funcionaria.getNome(),
                funcionaria.getFotoUrl(),
                funcionaria.getEspecialidades(),
                funcionaria.getAtivo()
        );
    }
}