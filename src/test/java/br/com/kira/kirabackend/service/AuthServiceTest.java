package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.RefreshToken;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.domain.enums.Genero;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.request.RefreshTokenRequest;
import br.com.kira.kirabackend.dto.request.ServicoRegistroRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.exception.DocumentoJaCadastradoException;
import br.com.kira.kirabackend.exception.EmailJaCadastradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long REFRESH_EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ServicoRepository servicoRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "refreshExpiration", REFRESH_EXPIRATION_MS);
    }

    // ---------- helpers ----------

    private Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");
        cliente.setEmail("ana@teste.com");
        return cliente;
    }

    private Empresa empresa() {
        Empresa empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio Kira");
        empresa.setEmail("estudio@teste.com");
        return empresa;
    }

    private RefreshToken refreshToken(Usuario usuario, LocalDateTime expiracao, boolean revogado) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-antigo");
        refreshToken.setUsuario(usuario);
        refreshToken.setDataExpiracao(expiracao);
        refreshToken.setRevogado(revogado);
        return refreshToken;
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(KiraTimeZone.DEFAULT);
    }

    private ClienteRegistroRequest registroCliente() {
        return new ClienteRegistroRequest("Ana", "ana@teste.com", "senha12345", "11999999999",
                "123.456.789-00", LocalDate.of(1995, 5, 10), Genero.FEMININO, null, null, null);
    }

    private EmpresaRegistroRequest registroEmpresa(List<String> especialidades,
                                                   List<ServicoRegistroRequest> servicos) {
        return new EmpresaRegistroRequest("Estúdio Kira", "estudio@teste.com", "senha12345", "11988888888",
                "12.345.678/0001-90", null, "Estúdio de beleza", null, null, null, null,
                especialidades, servicos, TipoEstabelecimento.ESTABELECIMENTO);
    }

    private Servico registrarEmpresaComUmServico(String preco, String duracao) {
        when(usuarioRepository.existsByEmailIgnoreCase("estudio@teste.com")).thenReturn(false);

        service.registrarEmpresa(registroEmpresa(null,
                List.of(new ServicoRegistroRequest("Corte", preco, duracao))));

        ArgumentCaptor<Servico> captor = ArgumentCaptor.forClass(Servico.class);
        verify(servicoRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- login ----------

    @Nested
    @DisplayName("login")
    class Login {

        private LoginResponse logar(Usuario usuario, String tipoEsperado) {
            when(authenticationManager.authenticate(any()))
                    .thenReturn(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
            when(usuarioRepository.findByEmailIgnoreCase(usuario.getEmail())).thenReturn(Optional.of(usuario));
            when(jwtTokenProvider.gerarToken(usuario, tipoEsperado)).thenReturn("jwt-" + tipoEsperado);

            return service.login(new LoginRequest(usuario.getEmail(), "senha12345"));
        }

        @Test
        @DisplayName("Login de cliente devolve o token, o refresh token e o tipo CLIENTE")
        void loginCliente() {
            Cliente cliente = cliente();

            LoginResponse response = logar(cliente, "CLIENTE");

            assertEquals("jwt-CLIENTE", response.token());
            assertNotNull(response.refreshToken());
            assertEquals("CLIENTE", response.tipo());
            assertEquals("Ana", response.nome());
            assertEquals("ana@teste.com", response.email());

            ArgumentCaptor<UsernamePasswordAuthenticationToken> credenciais =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(credenciais.capture());
            assertEquals("ana@teste.com", credenciais.getValue().getPrincipal());
            assertEquals("senha12345", credenciais.getValue().getCredentials());
        }

        @Test
        @DisplayName("Login de empresa devolve o tipo EMPRESA")
        void loginEmpresa() {
            LoginResponse response = logar(empresa(), "EMPRESA");

            assertEquals("jwt-EMPRESA", response.token());
            assertEquals("EMPRESA", response.tipo());
        }

        @Test
        @DisplayName("Login apaga o refresh token anterior antes de criar e salvar o novo")
        void loginApagaRefreshAnteriorAntesDeCriarNovo() {
            Cliente cliente = cliente();

            LoginResponse response = logar(cliente, "CLIENTE");

            InOrder ordem = inOrder(refreshTokenRepository);
            ordem.verify(refreshTokenRepository).deleteByUsuarioId(cliente.getId());
            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            ordem.verify(refreshTokenRepository).save(captor.capture());

            RefreshToken salvo = captor.getValue();
            assertEquals(response.refreshToken(), salvo.getToken());
            assertSame(cliente, salvo.getUsuario());
            assertFalse(salvo.getRevogado());
            assertTrue(salvo.getDataExpiracao().isAfter(agora().plusDays(6)));
        }
    }

    // ---------- refresh ----------

    @Nested
    @DisplayName("refreshToken")
    class Refresh {

        @Test
        @DisplayName("Refresh válido gera um novo token e troca o refresh token")
        void refreshValido() {
            Cliente cliente = cliente();
            when(refreshTokenRepository.findByToken("refresh-antigo"))
                    .thenReturn(Optional.of(refreshToken(cliente, agora().plusDays(1), false)));
            when(jwtTokenProvider.gerarToken(cliente, "CLIENTE")).thenReturn("jwt-novo");

            LoginResponse response = service.refreshToken(new RefreshTokenRequest("refresh-antigo"));

            assertEquals("jwt-novo", response.token());
            assertEquals("CLIENTE", response.tipo());
            assertNotEquals("refresh-antigo", response.refreshToken());
            verify(refreshTokenRepository).deleteByUsuarioId(cliente.getId());
        }

        @Test
        @DisplayName("Refresh com token inexistente dá erro")
        void refreshInexistente() {
            when(refreshTokenRepository.findByToken("nao-existe")).thenReturn(Optional.empty());

            RefreshTokenRequest req = new RefreshTokenRequest("nao-existe");

            assertThrows(RegraDeNegocioException.class,
                    () -> service.refreshToken(req));
            verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("Refresh com token expirado dá erro")
        void refreshExpirado() {
            when(refreshTokenRepository.findByToken("refresh-antigo"))
                    .thenReturn(Optional.of(refreshToken(cliente(), agora().minusMinutes(1), false)));

            RefreshTokenRequest req = new RefreshTokenRequest("refresh-antigo");

            assertThrows(RegraDeNegocioException.class,
                    () -> service.refreshToken(req));
            verifyNoInteractions(jwtTokenProvider);
            verify(refreshTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("Refresh com token revogado dá erro")
        void refreshRevogado() {
            when(refreshTokenRepository.findByToken("refresh-antigo"))
                    .thenReturn(Optional.of(refreshToken(cliente(), agora().plusDays(1), true)));

            RefreshTokenRequest req = new RefreshTokenRequest("refresh-antigo");

            assertThrows(RegraDeNegocioException.class,
                    () -> service.refreshToken(req));
            verifyNoInteractions(jwtTokenProvider);
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // ---------- logout ----------

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("Logout revoga o refresh token")
        void logoutRevogaToken() {
            RefreshToken refreshToken = refreshToken(cliente(), agora().plusDays(1), false);
            when(refreshTokenRepository.findByToken("refresh-antigo")).thenReturn(Optional.of(refreshToken));

            service.logout(new RefreshTokenRequest("refresh-antigo"));

            assertTrue(refreshToken.getRevogado());
            verify(refreshTokenRepository).save(refreshToken);
        }

        @Test
        @DisplayName("Logout com token inexistente não faz nada nem dá erro")
        void logoutTokenInexistente() {
            when(refreshTokenRepository.findByToken("nao-existe")).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> service.logout(new RefreshTokenRequest("nao-existe")));
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // ---------- cadastro ----------

    @Nested
    @DisplayName("cadastro")
    class Cadastro {

        @Test
        @DisplayName("Cadastro de cliente com e-mail repetido dá erro de e-mail já cadastrado")
        void clienteEmailRepetido() {
            when(usuarioRepository.existsByEmailIgnoreCase("ana@teste.com")).thenReturn(true);

            ClienteRegistroRequest req = registroCliente();

            EmailJaCadastradoException ex = assertThrows(EmailJaCadastradoException.class,
                    () -> service.registrarCliente(req));
            assertEquals("Email já cadastrado", ex.getMessage());
            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("Cadastro de empresa com e-mail repetido dá erro de e-mail já cadastrado")
        void empresaEmailRepetido() {
            when(usuarioRepository.existsByEmailIgnoreCase("estudio@teste.com")).thenReturn(true);

            EmpresaRegistroRequest req = registroEmpresa(null, null);

            assertThrows(EmailJaCadastradoException.class,
                    () -> service.registrarEmpresa(req));
            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(servicoRepository, emailService);
        }

        @Test
        @DisplayName("Cadastro de cliente salva a senha criptografada, nunca em texto puro")
        void clienteSenhaCriptografada() {
            when(usuarioRepository.existsByEmailIgnoreCase("ana@teste.com")).thenReturn(false);
            when(passwordEncoder.encode("senha12345")).thenReturn("$2a$10$hash");

            service.registrarCliente(registroCliente());

            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            Cliente salvo = assertInstanceOf(Cliente.class, captor.getValue());
            assertEquals("$2a$10$hash", salvo.getSenhaHash());
            assertNotEquals("senha12345", salvo.getSenhaHash());
            verify(emailService).enviarEmailBoasVindas("ana@teste.com", "Ana");
        }

        @Test
        @DisplayName("Cadastro de empresa salva a senha criptografada, nunca em texto puro")
        void empresaSenhaCriptografada() {
            when(usuarioRepository.existsByEmailIgnoreCase("estudio@teste.com")).thenReturn(false);
            when(passwordEncoder.encode("senha12345")).thenReturn("$2a$10$hash");

            service.registrarEmpresa(registroEmpresa(null, null));

            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            assertEquals("$2a$10$hash", captor.getValue().getSenhaHash());
            assertNotEquals("senha12345", captor.getValue().getSenhaHash());
        }

        @Test
        @DisplayName("Especialidades são juntas com \", \"")
        void especialidadesJuntasComVirgula() {
            when(usuarioRepository.existsByEmailIgnoreCase("estudio@teste.com")).thenReturn(false);

            service.registrarEmpresa(registroEmpresa(List.of("Cílios", "Sobrancelha", "Unhas"), null));

            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            assertEquals("Cílios, Sobrancelha, Unhas", ((Empresa) captor.getValue()).getEspecialidades());
        }

        @Test
        @DisplayName("Lista de especialidades vazia não grava nada")
        void especialidadesVazias() {
            when(usuarioRepository.existsByEmailIgnoreCase("estudio@teste.com")).thenReturn(false);

            service.registrarEmpresa(registroEmpresa(List.of(), null));

            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            assertNull(((Empresa) captor.getValue()).getEspecialidades());
        }

        @Test
        @DisplayName("Cadastro de empresa cria os serviços ligados à empresa")
        void empresaCriaServicos() {
            when(usuarioRepository.existsByEmailIgnoreCase("estudio@teste.com")).thenReturn(false);

            service.registrarEmpresa(registroEmpresa(null, List.of(
                    new ServicoRegistroRequest("Corte", "R$ 50", "45 min"),
                    new ServicoRegistroRequest("Escova", "80,00", "1h"))));

            ArgumentCaptor<Usuario> empresaCaptor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(empresaCaptor.capture());
            Empresa empresa = (Empresa) empresaCaptor.getValue();

            ArgumentCaptor<Servico> servicoCaptor = ArgumentCaptor.forClass(Servico.class);
            verify(servicoRepository, times(2)).save(servicoCaptor.capture());
            List<Servico> servicos = servicoCaptor.getAllValues();

            assertEquals(List.of("Corte", "Escova"), servicos.stream().map(Servico::getNome).toList());
            assertTrue(servicos.stream().allMatch(s -> s.getEmpresa() == empresa));
        }

        @Test
        @DisplayName("Cadastro de empresa sem serviços não salva nenhum serviço")
        void empresaSemServicos() {
            when(usuarioRepository.existsByEmailIgnoreCase("estudio@teste.com")).thenReturn(false);

            service.registrarEmpresa(registroEmpresa(null, null));

            verifyNoInteractions(servicoRepository);
            verify(emailService).enviarEmailBoasVindas(eq("estudio@teste.com"), anyString());
        }

        @Test
        @DisplayName("E-mail é salvo em minúsculas e sem espaços, e a checagem de repetido usa esse valor")
        void emailNormalizado() {
            service.registrarCliente(new ClienteRegistroRequest("Ana", "  Ana@Teste.COM ", "senha12345",
                    "11999999999", "123.456.789-00", LocalDate.of(1995, 5, 10), Genero.FEMININO,
                    null, null, null));

            verify(usuarioRepository).existsByEmailIgnoreCase("ana@teste.com");
            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            assertEquals("ana@teste.com", captor.getValue().getEmail());
        }

        @Test
        @DisplayName("Cadastro de cliente com CPF repetido dá erro de CPF já cadastrado")
        void clienteCpfRepetido() {
            when(usuarioRepository.existsClienteByCpf("123.456.789-00")).thenReturn(true);

            DocumentoJaCadastradoException ex = assertThrows(DocumentoJaCadastradoException.class,
                    () -> service.registrarCliente(registroCliente()));
            assertEquals("CPF já cadastrado", ex.getMessage());
            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("Cadastro de empresa com CNPJ repetido dá erro de CNPJ já cadastrado")
        void empresaCnpjRepetido() {
            when(usuarioRepository.existsEmpresaByCnpj("12.345.678/0001-90")).thenReturn(true);

            DocumentoJaCadastradoException ex = assertThrows(DocumentoJaCadastradoException.class,
                    () -> service.registrarEmpresa(registroEmpresa(null, null)));
            assertEquals("CNPJ já cadastrado", ex.getMessage());
            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(servicoRepository, emailService);
        }

        @Test
        @DisplayName("Cadastro de empresa com CPF repetido dá erro de CPF já cadastrado")
        void empresaCpfRepetido() {
            when(usuarioRepository.existsEmpresaByCpf("123.456.789-00")).thenReturn(true);

            DocumentoJaCadastradoException ex = assertThrows(DocumentoJaCadastradoException.class,
                    () -> service.registrarEmpresa(new EmpresaRegistroRequest("Estúdio Kira", "estudio@teste.com",
                            "senha12345", "11988888888", null, "123.456.789-00", null, null, null, null,
                            null, null, null, TipoEstabelecimento.AUTONOMA)));
            assertEquals("CPF já cadastrado", ex.getMessage());
            verify(usuarioRepository, never()).save(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("CNPJ e CPF vazios da empresa viram null e não são checados")
        void empresaDocumentosVaziosViramNulo(String vazio) {
            service.registrarEmpresa(new EmpresaRegistroRequest("Estúdio Kira", "estudio@teste.com",
                    "senha12345", "11988888888", vazio, vazio, null, null, null, null,
                    null, null, null, TipoEstabelecimento.ESTABELECIMENTO));

            verify(usuarioRepository, never()).existsEmpresaByCnpj(any());
            verify(usuarioRepository, never()).existsEmpresaByCpf(any());
            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            Empresa salva = (Empresa) captor.getValue();
            assertNull(salva.getCnpj());
            assertNull(salva.getCpf());
        }
    }

    // ---------- conversão de preço e duração (bugs 2 e 3) ----------

    @Nested
    @DisplayName("conversão de preço e duração dos serviços")
    class Conversao {

        @ParameterizedTest(name = "\"{0}\" = R$ {1}")
        @CsvSource({
                "'R$ 50',       50.00",
                "'45,00',       45.00",
                "'1.234,56',    1234.56",
                "'R$ 1.234,56', 1234.56",
                "'50.00',       50.00",
                "'1.234.567',   1234567.00",
                "'R$ 0,99',     0.99"
        })
        @DisplayName("Converte o preço digitado")
        void deveConverterPreco(String bruto, String esperado) {
            Servico servico = registrarEmpresaComUmServico(bruto, "30 min");

            assertEquals(0, new BigDecimal(esperado).compareTo(servico.getPreco()),
                    () -> "esperado " + esperado + " mas foi " + servico.getPreco());
        }

        @ParameterizedTest(name = "\"{0}\" = R$ 0")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "a combinar"})
        @DisplayName("Preço vazio, nulo ou sem número vira zero")
        void precoVazioViraZero(String bruto) {
            Servico servico = registrarEmpresaComUmServico(bruto, "30 min");

            assertEquals(0, BigDecimal.ZERO.compareTo(servico.getPreco()));
        }

        @ParameterizedTest(name = "\"{0}\" = {1} min")
        @CsvSource({
                "'45 min',      45",
                "'45',          45",
                "'90 minutos',  90",
                "'1h',          60",
                "'1h30',        90",
                "'1h 30min',    90",
                "'1 h 15 min',  75",
                "'2 horas',     120",
                "'2H',          120"
        })
        @DisplayName("Converte a duração digitada para minutos")
        void deveConverterDuracao(String bruto, int esperado) {
            Servico servico = registrarEmpresaComUmServico("50", bruto);

            assertEquals(esperado, servico.getDuracaoMinutos());
        }

        @ParameterizedTest(name = "\"{0}\" = 0 min")
        @NullAndEmptySource
        @ValueSource(strings = {"a combinar"})
        @DisplayName("Duração vazia, nula ou sem número vira zero")
        void duracaoVaziaViraZero(String bruto) {
            Servico servico = registrarEmpresaComUmServico("50", bruto);

            assertEquals(0, servico.getDuracaoMinutos());
        }

        @ParameterizedTest(name = "\"{0}\" = 0 min")
        @ValueSource(strings = {"99999999999", "99999999999h", "40000000h", "1h99999999999"})
        @DisplayName("Duração com número grande demais vira zero em vez de estourar")
        void duracaoGrandeDemaisViraZero(String bruto) {
            Servico servico = registrarEmpresaComUmServico("50", bruto);

            assertEquals(0, servico.getDuracaoMinutos());
        }

        @Test
        @DisplayName("Duração gigante é processada rápido (sem backtracking na regex)")
        void duracaoGiganteNaoTravaARegex() {
            String bruto = "1".repeat(100_000);

            Servico servico = assertTimeoutPreemptively(Duration.ofSeconds(1),
                    () -> registrarEmpresaComUmServico("50", bruto));
            assertEquals(0, servico.getDuracaoMinutos());
        }
    }
}
