package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Avaliacao;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.ConsentimentoProvador;
import br.com.kira.kirabackend.domain.entity.DisponibilidadeEstudio;
import br.com.kira.kirabackend.domain.entity.DispositivoNotificacao;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Favorita;
import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.entity.VerificacaoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoAvaliador;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.dto.response.AvaliacaoResponse;
import br.com.kira.kirabackend.dto.response.ContatoEmergenciaResponse;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse.Conta;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse.Consentimentos;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse.Dispositivo;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse.Estudio;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse.EstudioFavorito;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse.Provador;
import br.com.kira.kirabackend.dto.response.DadosPessoaisResponse.Verificacao;
import br.com.kira.kirabackend.dto.response.DisponibilidadeResponse;
import br.com.kira.kirabackend.dto.response.EnderecoResponse;
import br.com.kira.kirabackend.dto.response.FotoPortfolioResponse;
import br.com.kira.kirabackend.dto.response.FuncionariaResponse;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.AgendamentoRepository;
import br.com.kira.kirabackend.repository.AvaliacaoRepository;
import br.com.kira.kirabackend.repository.ConsentimentoProvadorRepository;
import br.com.kira.kirabackend.repository.ContatoEmergenciaRepository;
import br.com.kira.kirabackend.repository.DisponibilidadeEstudioRepository;
import br.com.kira.kirabackend.repository.DispositivoNotificacaoRepository;
import br.com.kira.kirabackend.repository.FavoritaRepository;
import br.com.kira.kirabackend.repository.FotoPortfolioRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.repository.MensagemRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Exportação dos dados pessoais da conta logada (LGPD: direito de acesso e portabilidade).
 * Junta numa resposta só o que está espalhado pelas tabelas, sem nada que sirva para entrar na conta.
 */
@Service
@RequiredArgsConstructor
public class DadosPessoaisService {

    private final UsuarioRepository usuarioRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final MensagemRepository mensagemRepository;
    private final FavoritaRepository favoritaRepository;
    private final ContatoEmergenciaRepository contatoEmergenciaRepository;
    private final ConsentimentoProvadorRepository consentimentoProvadorRepository;
    private final VerificacaoIdentidadeRepository verificacaoRepository;
    private final DispositivoNotificacaoRepository dispositivoRepository;
    private final FotoPortfolioRepository fotoPortfolioRepository;
    private final ServicoRepository servicoRepository;
    private final FuncionariaRepository funcionariaRepository;
    private final DisponibilidadeEstudioRepository disponibilidadeRepository;

    // Lê várias relações LAZY (nome do estúdio, do serviço etc.)
    @Transactional(readOnly = true)
    public DadosPessoaisResponse exportar(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
        if (usuario instanceof Cliente cliente) {
            return exportarCliente(cliente);
        }
        if (usuario instanceof Empresa empresa) {
            return exportarEstudio(empresa);
        }
        throw new RegraDeNegocioException("Disponível só para contas de cliente e de estúdio");
    }

    private DadosPessoaisResponse exportarCliente(Cliente c) {
        UUID id = c.getId();
        Conta conta = new Conta(c.getId(), c.getNome(), c.getEmail(), c.getTelefone(), c.getCpf(), null,
                c.getDataNascimento(), c.getGenero(), EnderecoResponse.from(c.getEndereco()),
                c.getLatitude(), c.getLongitude(), c.getFotoUrl(), c.getDataCriacao(), null, List.of(), null,
                PerfilService.emOrdem(c.getPreferencias()));
        return new DadosPessoaisResponse(
                agora(),
                TipoUsuario.CLIENTE,
                conta,
                agendamentoRepository.findByClienteId(id).stream().map(AgendamentoResponse::from).toList(),
                avaliacoes(avaliacaoRepository.findByClienteIdAndTipoAvaliador(id, TipoAvaliador.CLIENTE)),
                avaliacoes(avaliacaoRepository.findByClienteIdAndTipoAvaliador(id, TipoAvaliador.EMPRESA)),
                mensagens(id),
                favoritaRepository.findByClienteIdOrderByDataCriacaoDesc(id).stream()
                        .map(DadosPessoaisService::paraFavorita).toList(),
                contatoEmergenciaRepository.findByClienteIdOrderByDataCriacaoAsc(id).stream()
                        .map(ContatoEmergenciaResponse::from).toList(),
                consentimentos(id),
                dispositivos(id),
                fotos(c.getFotoUrl(), List.of()),
                null);
    }

    private DadosPessoaisResponse exportarEstudio(Empresa e) {
        UUID id = e.getId();
        Conta conta = new Conta(e.getId(), e.getNome(), e.getEmail(), e.getTelefone(), e.getCpf(), e.getCnpj(),
                e.getDataNascimento(), null, EnderecoResponse.from(e.getEndereco()),
                e.getLatitude(), e.getLongitude(), e.getFotoUrl(), e.getDataCriacao(), e.getDescricao(),
                PerfilService.separarEspecialidades(e.getEspecialidades()), e.getTipoEstabelecimento(), null);
        List<FotoPortfolio> portfolio = fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(id);
        List<Servico> servicos = servicoRepository.findByEmpresaId(id);
        Estudio estudio = new Estudio(
                servicos.stream().map(ServicoResponse::from).toList(),
                funcionariaRepository.findByEmpresaId(id).stream().map(DadosPessoaisService::paraFuncionaria).toList(),
                portfolio.stream()
                        .map(f -> new FotoPortfolioResponse(f.getId(), f.getUrl(), f.getDataCriacao())).toList(),
                disponibilidadeRepository.findByEmpresaId(id).stream().map(DadosPessoaisService::paraHorario).toList());
        return new DadosPessoaisResponse(
                agora(),
                TipoUsuario.EMPRESA,
                conta,
                agendamentoRepository.findByEmpresaId(id).stream().map(AgendamentoResponse::from).toList(),
                avaliacoes(avaliacaoRepository.findByEmpresaIdAndTipoAvaliador(id, TipoAvaliador.EMPRESA)),
                avaliacoes(avaliacaoRepository.findByEmpresaIdAndTipoAvaliador(id, TipoAvaliador.CLIENTE)),
                mensagens(id),
                List.of(),
                List.of(),
                consentimentos(id),
                dispositivos(id),
                fotos(e.getFotoUrl(), Stream.concat(
                        portfolio.stream().map(FotoPortfolio::getUrl),
                        servicos.stream().map(Servico::getFotoUrl).filter(Objects::nonNull)).toList()),
                estudio);
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT);
    }

    private static List<AvaliacaoResponse> avaliacoes(List<Avaliacao> lista) {
        return lista.stream().map(AvaliacaoResponse::from).toList();
    }

    private List<MensagemResponse> mensagens(UUID usuarioId) {
        return mensagemRepository.findByRemetenteIdOrderByDataEnvioAsc(usuarioId).stream()
                .map(MensagemResponse::from).toList();
    }

    private Consentimentos consentimentos(UUID usuarioId) {
        Provador provador = consentimentoProvadorRepository.findByUsuarioId(usuarioId)
                .map(DadosPessoaisService::paraProvador)
                .orElse(null);
        List<Verificacao> verificacoes = verificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId)
                .stream().map(DadosPessoaisService::paraVerificacao).toList();
        return new Consentimentos(provador, verificacoes);
    }

    private List<Dispositivo> dispositivos(UUID usuarioId) {
        return dispositivoRepository.findByUsuarioIdOrderByDataCriacaoAsc(usuarioId).stream()
                .map(d -> new Dispositivo(d.getId(), servicoDeNotificacao(d), d.getDataCriacao()))
                .toList();
    }

    // O endereço completo funciona como credencial para enviar notificações: sai só o domínio
    private static String servicoDeNotificacao(DispositivoNotificacao dispositivo) {
        try {
            return URI.create(dispositivo.getEndpoint()).getHost();
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static List<String> fotos(String fotoPerfil, List<String> outras) {
        List<String> fotos = new ArrayList<>();
        if (fotoPerfil != null) {
            fotos.add(fotoPerfil);
        }
        fotos.addAll(outras);
        return fotos;
    }

    private static EstudioFavorito paraFavorita(Favorita f) {
        return new EstudioFavorito(f.getEmpresa().getId(), f.getEmpresa().getNome(), f.getDataCriacao());
    }

    private static Provador paraProvador(ConsentimentoProvador c) {
        return new Provador(c.getVersao(), c.getAceitoEm(), c.getRevogadoEm());
    }

    private static Verificacao paraVerificacao(VerificacaoIdentidade v) {
        return new Verificacao(v.getTipoDocumento(), v.getStatus(), v.getMotivos(), v.getVersaoConsentimento(),
                v.getConsentimentoAceitoEm(), v.getDataCriacao());
    }

    private static FuncionariaResponse paraFuncionaria(Funcionaria f) {
        return new FuncionariaResponse(f.getId(), f.getNome(), f.getFotoUrl(), f.getEspecialidades(),
                f.getAtivo(), f.getComissaoPercentual());
    }

    private static DisponibilidadeResponse paraHorario(DisponibilidadeEstudio d) {
        return new DisponibilidadeResponse(d.getId(), d.getDiaSemana(), d.getHoraInicio(), d.getHoraFim());
    }
}
