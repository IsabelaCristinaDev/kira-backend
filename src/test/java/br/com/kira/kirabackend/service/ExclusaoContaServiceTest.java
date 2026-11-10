package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.Genero;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExclusaoContaServiceTest {

    private static final String SENHA = "Senha1234";

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AgendamentoService agendamentoService;
    @Mock private ContatoEmergenciaRepository contatoEmergenciaRepository;
    @Mock private FavoritaRepository favoritaRepository;
    @Mock private DispositivoNotificacaoRepository dispositivoRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenRecuperacaoSenhaRepository tokenRecuperacaoSenhaRepository;
    @Mock private ConsentimentoProvadorRepository consentimentoProvadorRepository;
    @Mock private UsoProvadorRepository usoProvadorRepository;
    @Mock private VerificacaoIdentidadeRepository verificacaoRepository;
    @Mock private FotoPortfolioRepository fotoPortfolioRepository;
    @Mock private FuncionariaRepository funcionariaRepository;
    @Mock private ServicoRepository servicoRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private ExclusaoContaService service;

    @BeforeEach
    void setUp() {
        service = new ExclusaoContaService(usuarioRepository, passwordEncoder, agendamentoService,
                contatoEmergenciaRepository, favoritaRepository, dispositivoRepository, refreshTokenRepository,
                tokenRecuperacaoSenhaRepository, consentimentoProvadorRepository, usoProvadorRepository,
                verificacaoRepository, fotoPortfolioRepository, funcionariaRepository, servicoRepository,
                eventPublisher);
    }

    private Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");
        cliente.setEmail("ana@kira.com");
        cliente.setSenhaHash(passwordEncoder.encode(SENHA));
        cliente.setTelefone("62999990000");
        cliente.setCpf("52998224725");
        cliente.setDataNascimento(LocalDate.of(1995, 1, 1));
        cliente.setGenero(Genero.FEMININO);
        cliente.setEndereco(new Endereco("74000000", "Rua A", "1", null, "Centro", "Goiania", "GO"));
        cliente.setLatitude(-16.6);
        cliente.setLongitude(-49.2);
        cliente.setFotoUrl("/api/arquivos/perfil.jpg");
        cliente.setAtivo(true);
        when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
        return cliente;
    }

    private Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio Bela");
        empresa.setEmail("bela@kira.com");
        empresa.setSenhaHash(passwordEncoder.encode(SENHA));
        empresa.setTelefone("62999990001");
        empresa.setCpf("39053344705");
        empresa.setCnpj("11222333000181");
        empresa.setDescricao("Unhas");
        empresa.setEspecialidades("Unha");
        empresa.setEndereco(new Endereco("74000000", "Rua B", "2", null, "Centro", "Goiania", "GO"));
        empresa.setLatitude(-16.6);
        empresa.setLongitude(-49.2);
        empresa.setTipoEstabelecimento(TipoEstabelecimento.AUTONOMA);
        empresa.setFotoUrl("/api/arquivos/logo.png");
        empresa.setAtivo(true);
        when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
        return empresa;
    }

    private List<String> arquivosApagados() {
        ArgumentCaptor<ContaExcluidaEvent> evento = ArgumentCaptor.forClass(ContaExcluidaEvent.class);
        verify(eventPublisher).publishEvent(evento.capture());
        return evento.getValue().arquivos();
    }

    @Test
    @DisplayName("Senha errada: recusa com 400 e não mexe em nada")
    void senhaErrada() {
        Cliente cliente = cliente();

        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                () -> service.excluir(cliente.getId(), "outra-senha"));

        assertEquals("Senha incorreta", erro.getMessage());
        assertEquals("Ana", cliente.getNome());
        assertTrue(cliente.getAtivo());
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(agendamentoService, contatoEmergenciaRepository, eventPublisher);
    }

    @Test
    @DisplayName("Senha vazia também é recusada")
    void senhaVazia() {
        Cliente cliente = cliente();

        assertThrows(RegraDeNegocioException.class, () -> service.excluir(cliente.getId(), null));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Conta inexistente responde 404")
    void contaInexistente() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.excluir(id, SENHA));
    }

    @Test
    @DisplayName("Conta de administrador não é excluída por aqui")
    void admin() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        admin.setSenhaHash(passwordEncoder.encode(SENHA));
        when(usuarioRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(RegraDeNegocioException.class, () -> service.excluir(admin.getId(), SENHA));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cliente: anonimiza, desativa, cancela os futuros e apaga o que é só dela")
    void cliente_anonimiza() {
        Cliente cliente = cliente();
        String hashAntigo = cliente.getSenhaHash();
        UUID id = cliente.getId();

        service.excluir(id, SENHA);

        assertEquals(ExclusaoContaService.NOME_CONTA_EXCLUIDA, cliente.getNome());
        assertFalse(cliente.getEmail().contains("@"));
        assertTrue(cliente.getEmail().contains(id.toString()));
        assertNotEquals("52998224725", cliente.getCpf());
        assertFalse(cliente.getCpf().chars().allMatch(Character::isDigit));
        assertTrue(cliente.getCpf().length() <= 14);
        assertNotEquals(hashAntigo, cliente.getSenhaHash());
        assertFalse(passwordEncoder.matches(SENHA, cliente.getSenhaHash()));
        assertNull(cliente.getTelefone());
        assertNull(cliente.getDataNascimento());
        assertNull(cliente.getGenero());
        assertNull(cliente.getEndereco());
        assertNull(cliente.getLatitude());
        assertNull(cliente.getLongitude());
        assertNull(cliente.getFotoUrl());
        assertFalse(cliente.getAtivo());
        verify(usuarioRepository).save(cliente);

        verify(agendamentoService).cancelarFuturosDaContaExcluida(cliente);
        verify(contatoEmergenciaRepository).deleteByClienteId(id);
        verify(favoritaRepository).deleteByClienteId(id);
        verify(dispositivoRepository).deleteByUsuarioId(id);
        verify(refreshTokenRepository).deleteByUsuarioId(id);
        verify(tokenRecuperacaoSenhaRepository).deleteByUsuarioId(id);
        verify(consentimentoProvadorRepository).deleteByUsuarioId(id);
        verify(usoProvadorRepository).deleteById(id);
        verify(verificacaoRepository).deleteByUsuarioId(id);
        verifyNoInteractions(fotoPortfolioRepository, funcionariaRepository, servicoRepository);

        assertEquals(List.of("/api/arquivos/perfil.jpg"), arquivosApagados());
    }

    @Test
    @DisplayName("Estúdio: anonimiza, some da vitrine, apaga portfólio e fotos das funcionárias")
    void estudio_anonimiza() {
        Empresa empresa = empresa();
        UUID id = empresa.getId();
        FotoPortfolio foto = new FotoPortfolio();
        foto.setUrl("/api/arquivos/trabalho.jpg");
        when(fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(id)).thenReturn(List.of(foto));
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setNome("Carla");
        funcionaria.setFotoUrl("/api/arquivos/carla.jpg");
        funcionaria.setAtivo(true);
        when(funcionariaRepository.findByEmpresaId(id)).thenReturn(List.of(funcionaria));
        Servico servico = new Servico();
        servico.setAtivo(true);
        when(servicoRepository.findByEmpresaId(id)).thenReturn(List.of(servico));

        service.excluir(id, SENHA);

        assertEquals(ExclusaoContaService.NOME_CONTA_EXCLUIDA, empresa.getNome());
        assertFalse(empresa.getEmail().contains("@"));
        assertNull(empresa.getCpf());
        assertNull(empresa.getCnpj());
        assertNull(empresa.getTelefone());
        assertNull(empresa.getDescricao());
        assertNull(empresa.getEspecialidades());
        assertNull(empresa.getEndereco());
        assertNull(empresa.getLatitude());
        assertNull(empresa.getLongitude());
        assertNull(empresa.getFotoUrl());
        assertFalse(empresa.getAtivo());
        assertFalse(funcionaria.getAtivo());
        assertNull(funcionaria.getFotoUrl());
        assertFalse(servico.getAtivo());

        verify(agendamentoService).cancelarFuturosDaContaExcluida(empresa);
        verify(fotoPortfolioRepository).deleteAll(List.of(foto));
        verify(favoritaRepository).deleteByEmpresaId(id);
        verify(dispositivoRepository).deleteByUsuarioId(id);
        verify(refreshTokenRepository).deleteByUsuarioId(id);
        verifyNoInteractions(contatoEmergenciaRepository);

        assertEquals(List.of("/api/arquivos/logo.png", "/api/arquivos/trabalho.jpg", "/api/arquivos/carla.jpg"),
                arquivosApagados());
    }
}
