package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.exception.TentativasExcedidasException;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ProtecaoLoginTest {

    private static final String IP = "200.1.1.1";
    private static final String EMAIL = "ana@teste.com";

    @Mock
    private EmailService emailService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private VerificacaoHumana verificacaoHumana;

    private final AtomicLong agoraNanos = new AtomicLong();
    // As pausas são anotadas em vez de esperadas: o teste não dorme
    private final List<Duration> pausas = new ArrayList<>();
    private ProtecaoLogin protecao;

    @BeforeEach
    void setUp() {
        LimiteDeTentativas limite = new LimiteDeTentativas(LimiteDeTentativasTest.PROPRIEDADES, agoraNanos::get);
        protecao = new ProtecaoLogin(limite, verificacaoHumana, emailService, usuarioRepository, pausas::add);
    }

    private static Supplier<String> loginErrado() {
        return () -> {
            throw new BadCredentialsException("senha errada");
        };
    }

    private void errar(String ip, int vezes) {
        for (int i = 0; i < vezes; i++) {
            assertThrows(BadCredentialsException.class, () -> protecao.proteger(ip, EMAIL, loginErrado()));
        }
    }

    @Test
    @DisplayName("Login certo devolve o resultado do login, sem atraso")
    void loginCerto() {
        String resultado = protecao.proteger(IP, EMAIL, () -> "token");

        assertEquals("token", resultado);
        assertTrue(pausas.isEmpty());
    }

    @Test
    @DisplayName("A 6ª tentativa do mesmo IP na mesma conta nem chega a conferir a senha")
    void sextaTentativaBloqueada(CapturedOutput saida) {
        errar(IP, 5);
        AtomicInteger chamadas = new AtomicInteger();

        TentativasExcedidasException erro = assertThrows(TentativasExcedidasException.class,
                () -> protecao.proteger(IP, EMAIL, () -> "token-" + chamadas.incrementAndGet()));

        assertEquals(0, chamadas.get());
        assertEquals(Duration.ofMinutes(15), erro.getEspera());
        // O log tem o IP e o e-mail mascarado; nunca o e-mail inteiro
        assertTrue(saida.getOut().contains(IP), saida.getOut());
        assertTrue(saida.getOut().contains("an***@teste.com"), saida.getOut());
        assertFalse(saida.getOut().contains(EMAIL), saida.getOut());
    }

    @Test
    @DisplayName("Com a conta sob ataque, o dono vindo de outro IP ainda entra, depois do atraso")
    void outroIpEntraComAtraso() {
        errar(IP, 5);
        when(verificacaoHumana.aprovada(anyString(), anyString())).thenReturn(true);

        String resultado = protecao.proteger("200.9.9.9", EMAIL, () -> "token");

        assertEquals("token", resultado);
        assertEquals(List.of(Duration.ofSeconds(1)), pausas);
    }

    @Test
    @DisplayName("Login certo zera as falhas daquele IP na conta")
    void sucessoZeraContador() {
        // A conta passa de 5 falhas no total: a verificação extra (aprovada) entra no caminho
        when(verificacaoHumana.aprovada(anyString(), anyString())).thenReturn(true);
        errar(IP, 4);
        protecao.proteger(IP, EMAIL, () -> "token");

        errar(IP, 4);

        assertEquals("token", protecao.proteger(IP, EMAIL, () -> "token"));
    }

    @Test
    @DisplayName("Com a conta marcada, a verificação extra é consultada antes do login")
    void verificacaoExtraConsultada() {
        errar("200.1.1.1", 3);
        errar("200.2.2.2", 2);
        when(verificacaoHumana.aprovada(anyString(), anyString())).thenReturn(true);

        protecao.proteger("200.3.3.3", EMAIL, () -> "token");

        verify(verificacaoHumana).aprovada("200.3.3.3", EMAIL);
    }

    @Test
    @DisplayName("Se a verificação extra reprovar, o login não acontece e conta como falha")
    void verificacaoExtraReprovada() {
        errar("200.1.1.1", 3);
        errar("200.2.2.2", 2);
        when(verificacaoHumana.aprovada(anyString(), anyString())).thenReturn(false);
        AtomicInteger chamadas = new AtomicInteger();

        assertThrows(BadCredentialsException.class,
                () -> protecao.proteger("200.3.3.3", EMAIL, () -> "token-" + chamadas.incrementAndGet()));

        assertEquals(0, chamadas.get());
    }

    @Test
    @DisplayName("Sem a conta marcada, a verificação extra nem é consultada")
    void semVerificacaoAntesDoLimite() {
        errar(IP, 4);

        protecao.proteger(IP, EMAIL, () -> "token");

        verify(verificacaoHumana, never()).aprovada(anyString(), anyString());
    }

    @Test
    @DisplayName("Na 10ª falha da conta, o dono recebe um e-mail de aviso, uma única vez")
    void avisoNaDecimaFalha() {
        Cliente ana = new Cliente();
        ana.setNome("Ana");
        ana.setEmail(EMAIL);
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(ana));
        when(verificacaoHumana.aprovada(anyString(), anyString())).thenReturn(true);

        // IPs diferentes: nenhum chega a ser bloqueado, e a conta soma as falhas
        for (int i = 0; i < 12; i++) {
            String ip = "200.0.0." + i;
            assertThrows(BadCredentialsException.class, () -> protecao.proteger(ip, EMAIL, loginErrado()));
        }

        verify(emailService, times(1)).enviarAvisoTentativasSuspeitas(EMAIL, "Ana");
    }

    @Test
    @DisplayName("Se o e-mail não tem conta, as falhas contam do mesmo jeito, mas nenhum aviso é enviado")
    void semContaSemAviso() {
        when(usuarioRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        when(verificacaoHumana.aprovada(anyString(), anyString())).thenReturn(true);

        for (int i = 0; i < 10; i++) {
            String ip = "200.0.0." + i;
            assertThrows(BadCredentialsException.class, () -> protecao.proteger(ip, EMAIL, loginErrado()));
        }

        verify(emailService, never()).enviarAvisoTentativasSuspeitas(any(), any());
    }
}
