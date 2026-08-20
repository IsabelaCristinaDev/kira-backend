package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.dto.request.FuncionariaRequest;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class FuncionariaService {

    private final FuncionariaRepository funcionariaRepository;
    private final UsuarioRepository usuarioRepository;

    public FuncionariaResponse cadastrar(UUID empresaId, FuncionariaRequest request) {
        Empresa empresa = (Empresa) usuarioRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa não encontrada"));

        if (funcionariaRepository.existsByEmpresaIdAndNomeIgnoreCaseAndAtivoTrue(empresaId, request.nome())) {
            throw new RegraDeNegocioException("Já existe uma funcionária ativa com esse nome");
        }

        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setNome(request.nome());
        funcionaria.setFotoUrl(request.fotoUrl());
        funcionaria.setEspecialidades(request.especialidades());
        funcionaria.setComissaoPercentual(request.comissaoPercentual());
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
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionária não encontrada"));

        funcionaria.setNome(request.nome());
        funcionaria.setFotoUrl(request.fotoUrl());
        funcionaria.setEspecialidades(request.especialidades());
        funcionaria.setComissaoPercentual(request.comissaoPercentual());

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
                funcionaria.getAtivo(),
                funcionaria.getComissaoPercentual()
        );
    }
}