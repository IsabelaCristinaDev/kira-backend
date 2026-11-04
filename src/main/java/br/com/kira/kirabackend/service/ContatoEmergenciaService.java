package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.ContatoEmergencia;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.ContatoEmergenciaRequest;
import br.com.kira.kirabackend.dto.response.ContatoEmergenciaResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.ContatoEmergenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ContatoEmergenciaService {

    private static final int MAXIMO_POR_CLIENTE = 5;

    private final ContatoEmergenciaRepository contatoRepository;

    @Transactional(readOnly = true)
    public List<ContatoEmergenciaResponse> listar(Usuario usuario) {
        exigirCliente(usuario);
        return contatoRepository.findByClienteIdOrderByDataCriacaoAsc(usuario.getId()).stream()
                .map(ContatoEmergenciaResponse::from)
                .toList();
    }

    @Transactional
    public ContatoEmergenciaResponse cadastrar(Usuario usuario, ContatoEmergenciaRequest request) {
        Cliente cliente = exigirCliente(usuario);
        if (contatoRepository.countByClienteId(cliente.getId()) >= MAXIMO_POR_CLIENTE) {
            throw new RegraDeNegocioException("Você pode ter no máximo " + MAXIMO_POR_CLIENTE + " contatos de emergência");
        }
        ContatoEmergencia contato = new ContatoEmergencia();
        contato.setCliente(cliente);
        contato.setNome(request.nome().trim());
        contato.setTelefone(request.telefone().trim());
        contato.setParentesco(request.parentesco() == null || request.parentesco().isBlank()
                ? null : request.parentesco().trim());
        return ContatoEmergenciaResponse.from(contatoRepository.save(contato));
    }

    @Transactional
    public void remover(Usuario usuario, UUID contatoId) {
        exigirCliente(usuario);
        // Contato de outra pessoa responde como inexistente
        ContatoEmergencia contato = contatoRepository.findByIdAndClienteId(contatoId, usuario.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Contato de emergência não encontrado"));
        contatoRepository.delete(contato);
    }

    private static Cliente exigirCliente(Usuario usuario) {
        if (usuario instanceof Cliente cliente) {
            return cliente;
        }
        throw new RegraDeNegocioException("Só clientes têm contatos de emergência");
    }
}
