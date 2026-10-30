package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.dto.request.ClientePerfilRequest;
import br.com.kira.kirabackend.dto.request.EmpresaPerfilRequest;
import br.com.kira.kirabackend.dto.request.EnderecoRequest;
import br.com.kira.kirabackend.dto.response.ClientePerfilResponse;
import br.com.kira.kirabackend.dto.response.EmpresaPerfilResponse;
import br.com.kira.kirabackend.dto.response.EnderecoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Dados da própria conta: cada usuário vê e edita só o próprio perfil.
 * O PUT troca todos os campos editáveis; endereço ausente apaga o endereço.
 */
@RequiredArgsConstructor
@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;

    public ClientePerfilResponse obterCliente(UUID clienteId, UUID usuarioId) {
        return toResponse(buscarCliente(clienteId, usuarioId));
    }

    @Transactional
    public ClientePerfilResponse atualizarCliente(UUID clienteId, UUID usuarioId, ClientePerfilRequest request) {
        Cliente cliente = buscarCliente(clienteId, usuarioId);
        cliente.setNome(request.nome().trim());
        cliente.setTelefone(request.telefone().trim());
        cliente.setDataNascimento(request.dataNascimento());
        cliente.setGenero(request.genero());
        cliente.setEndereco(paraEndereco(request.endereco()));
        cliente.setLatitude(request.latitude());
        cliente.setLongitude(request.longitude());
        return toResponse(usuarioRepository.save(cliente));
    }

    // O acesso à empresa já foi conferido no controller (AcessoEmpresa)
    public EmpresaPerfilResponse obterEmpresa(UUID empresaId) {
        return toResponse(buscarEmpresa(empresaId));
    }

    @Transactional
    public EmpresaPerfilResponse atualizarEmpresa(UUID empresaId, EmpresaPerfilRequest request) {
        Empresa empresa = buscarEmpresa(empresaId);
        empresa.setNome(request.nome().trim());
        empresa.setTelefone(request.telefone().trim());
        empresa.setDescricao(request.descricao());
        empresa.setEspecialidades(juntarEspecialidades(request.especialidades()));
        empresa.setDataNascimento(request.dataNascimento());
        empresa.setEndereco(paraEndereco(request.endereco()));
        empresa.setLatitude(request.latitude());
        empresa.setLongitude(request.longitude());
        empresa.setTipoEstabelecimento(request.tipoEstabelecimento());
        return toResponse(usuarioRepository.save(empresa));
    }

    private Cliente buscarCliente(UUID clienteId, UUID usuarioId) {
        if (!clienteId.equals(usuarioId)) {
            throw new RegraDeNegocioException("Você só pode acessar o seu próprio perfil");
        }
        if (!(usuarioRepository.findById(clienteId).orElse(null) instanceof Cliente cliente)) {
            throw new RecursoNaoEncontradoException("Cliente não encontrado");
        }
        return cliente;
    }

    private Empresa buscarEmpresa(UUID empresaId) {
        if (!(usuarioRepository.findById(empresaId).orElse(null) instanceof Empresa empresa)) {
            throw new RecursoNaoEncontradoException("Empresa não encontrada");
        }
        return empresa;
    }

    private static Endereco paraEndereco(EnderecoRequest request) {
        return request != null ? request.paraEndereco() : null;
    }

    // Mesmo formato do cadastro: "Unha, Cílios"; lista vazia apaga
    private static String juntarEspecialidades(List<String> especialidades) {
        if (especialidades == null) {
            return null;
        }
        List<String> limpas = especialidades.stream()
                .filter(e -> e != null && !e.isBlank())
                .map(String::trim)
                .toList();
        return limpas.isEmpty() ? null : String.join(", ", limpas);
    }

    private static List<String> separarEspecialidades(String especialidades) {
        if (especialidades == null || especialidades.isBlank()) {
            return List.of();
        }
        return Arrays.stream(especialidades.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .toList();
    }

    private static ClientePerfilResponse toResponse(Cliente c) {
        return new ClientePerfilResponse(
                c.getId(),
                c.getNome(),
                c.getEmail(),
                c.getTelefone(),
                c.getCpf(),
                c.getDataNascimento(),
                c.getGenero(),
                EnderecoResponse.from(c.getEndereco()),
                c.getLatitude(),
                c.getLongitude(),
                c.getFotoUrl());
    }

    private static EmpresaPerfilResponse toResponse(Empresa e) {
        return new EmpresaPerfilResponse(
                e.getId(),
                e.getNome(),
                e.getEmail(),
                e.getTelefone(),
                e.getCnpj(),
                e.getCpf(),
                e.getDescricao(),
                separarEspecialidades(e.getEspecialidades()),
                e.getDataNascimento(),
                EnderecoResponse.from(e.getEndereco()),
                e.getLatitude(),
                e.getLongitude(),
                e.getTipoEstabelecimento(),
                e.getFotoUrl());
    }
}
