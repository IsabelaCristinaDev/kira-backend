package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.observer.ContaExcluidaEvent;
import br.com.kira.kirabackend.repository.ConsentimentoProvadorRepository;
import br.com.kira.kirabackend.repository.ContatoEmergenciaRepository;
import br.com.kira.kirabackend.repository.DispositivoNotificacaoRepository;
import br.com.kira.kirabackend.repository.FavoritaRepository;
import br.com.kira.kirabackend.repository.FotoPortfolioRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.TokenRecuperacaoSenhaRepository;
import br.com.kira.kirabackend.repository.UsoProvadorRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Exclusão da conta a pedido da própria pessoa (LGPD: direito de eliminação).
 *
 * A linha da conta não é apagada: os agendamentos de outras pessoas apontam para ela. Os dados pessoais são
 * trocados ou apagados, a conta é desativada e o histórico de atendimentos continua com o nome "Conta excluída".
 * E-mail e documento recebem valores que não são reais, então ficam livres para um novo cadastro.
 */
@Service
@RequiredArgsConstructor
public class ExclusaoContaService {

    public static final String NOME_CONTA_EXCLUIDA = "Conta excluída";
    // O CPF da cliente não pode ser nulo nem repetido no banco: recebe um valor que nunca é um CPF de verdade
    private static final String PREFIXO_DOCUMENTO = "X";
    private static final int TAMANHO_DOCUMENTO = 14;

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgendamentoService agendamentoService;
    private final ContatoEmergenciaRepository contatoEmergenciaRepository;
    private final FavoritaRepository favoritaRepository;
    private final DispositivoNotificacaoRepository dispositivoRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenRecuperacaoSenhaRepository tokenRecuperacaoSenhaRepository;
    private final ConsentimentoProvadorRepository consentimentoProvadorRepository;
    private final UsoProvadorRepository usoProvadorRepository;
    private final VerificacaoIdentidadeRepository verificacaoRepository;
    private final FotoPortfolioRepository fotoPortfolioRepository;
    private final FuncionariaRepository funcionariaRepository;
    private final ServicoRepository servicoRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void excluir(UUID usuarioId, String senha) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
        if (!(usuario instanceof Cliente) && !(usuario instanceof Empresa)) {
            throw new RegraDeNegocioException("Disponível só para contas de cliente e de estúdio");
        }
        if (senha == null || senha.isBlank() || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
            throw new RegraDeNegocioException("Senha incorreta");
        }

        // Cancela antes de trocar o nome: o aviso à cliente ainda mostra quem era o estúdio
        agendamentoService.cancelarFuturosDaContaExcluida(usuario);

        List<String> arquivos = new ArrayList<>();
        if (usuario.getFotoUrl() != null) {
            arquivos.add(usuario.getFotoUrl());
        }
        apagarVinculosDaConta(usuarioId);
        if (usuario instanceof Cliente cliente) {
            contatoEmergenciaRepository.deleteByClienteId(usuarioId);
            favoritaRepository.deleteByClienteId(usuarioId);
            anonimizarCliente(cliente);
        } else if (usuario instanceof Empresa empresa) {
            arquivos.addAll(encerrarEstudio(empresa));
            anonimizarEstudio(empresa);
        }
        anonimizarConta(usuario);
        usuarioRepository.save(usuario);

        // As fotos saem do disco só depois do commit (ContaExcluidaEventListener)
        eventPublisher.publishEvent(new ContaExcluidaEvent(arquivos));
    }

    // Sessões, navegadores, consentimentos e uso do Provador: nada disso serve depois que a conta sai
    private void apagarVinculosDaConta(UUID usuarioId) {
        dispositivoRepository.deleteByUsuarioId(usuarioId);
        refreshTokenRepository.deleteByUsuarioId(usuarioId);
        tokenRecuperacaoSenhaRepository.deleteByUsuarioId(usuarioId);
        consentimentoProvadorRepository.deleteByUsuarioId(usuarioId);
        usoProvadorRepository.deleteById(usuarioId);
        verificacaoRepository.deleteByUsuarioId(usuarioId);
    }

    /** Apaga o portfólio, as favoritas e as fotos do estúdio e desativa funcionárias e serviços. Devolve as fotos a apagar. */
    private List<String> encerrarEstudio(Empresa empresa) {
        UUID id = empresa.getId();
        List<String> arquivos = new ArrayList<>();
        List<FotoPortfolio> portfolio = fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(id);
        portfolio.forEach(foto -> arquivos.add(foto.getUrl()));
        fotoPortfolioRepository.deleteAll(portfolio);
        favoritaRepository.deleteByEmpresaId(id);
        // Funcionárias e serviços ficam no banco porque o histórico de atendimentos aponta para eles
        for (Funcionaria funcionaria : funcionariaRepository.findByEmpresaId(id)) {
            if (funcionaria.getFotoUrl() != null) {
                arquivos.add(funcionaria.getFotoUrl());
            }
            funcionaria.setFotoUrl(null);
            funcionaria.setAtivo(false);
        }
        for (Servico servico : servicoRepository.findByEmpresaId(id)) {
            if (servico.getFotoUrl() != null) {
                arquivos.add(servico.getFotoUrl());
            }
            servico.setFotoUrl(null);
            servico.setAtivo(false);
        }
        return arquivos;
    }

    private void anonimizarCliente(Cliente cliente) {
        cliente.setCpf(documentoDescartavel());
        cliente.setDataNascimento(null);
        cliente.setGenero(null);
        cliente.setEndereco(null);
        cliente.setLatitude(null);
        cliente.setLongitude(null);
        cliente.getPreferencias().clear();
    }

    private static void anonimizarEstudio(Empresa empresa) {
        empresa.setCpf(null);
        empresa.setCnpj(null);
        empresa.setDataNascimento(null);
        empresa.setDescricao(null);
        empresa.setEspecialidades(null);
        empresa.setEndereco(null);
        empresa.setLatitude(null);
        empresa.setLongitude(null);
    }

    private void anonimizarConta(Usuario usuario) {
        usuario.setNome(NOME_CONTA_EXCLUIDA);
        // Sem "@": não é um e-mail, então nenhum envio chega a ninguém. O id garante que não se repete
        usuario.setEmail("conta-excluida-" + usuario.getId());
        // Senha aleatória que ninguém conhece: além de desativada, a conta não tem como ser acessada
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setTelefone(null);
        usuario.setFotoUrl(null);
        usuario.setAtivo(false);
    }

    private static String documentoDescartavel() {
        String aleatorio = UUID.randomUUID().toString().replace("-", "");
        return PREFIXO_DOCUMENTO + aleatorio.substring(0, TAMANHO_DOCUMENTO - PREFIXO_DOCUMENTO.length());
    }
}
