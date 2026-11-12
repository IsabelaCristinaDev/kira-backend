package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.ConsentimentoProvador;
import br.com.kira.kirabackend.dto.response.ConsentimentoProvadorResponse;
import br.com.kira.kirabackend.dto.response.GeracaoProvadorResponse;
import br.com.kira.kirabackend.exception.ConsentimentoProvadorNecessarioException;
import br.com.kira.kirabackend.exception.GeracaoProvadorException;
import br.com.kira.kirabackend.exception.LimiteProvadorException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.ConsentimentoProvadorRepository;
import br.com.kira.kirabackend.repository.UsoProvadorRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvadorServiceTest {

    private static final String VERSAO = "v1";
    private static final long LIMITE = 1024;
    private static final int LIMITE_DIARIO = 5;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @Mock
    private ConsentimentoProvadorRepository repository;

    @Mock
    private UsoProvadorRepository uso;

    @Mock
    private GeradorDeImagem gerador;

    private ProvadorService service;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        service = new ProvadorService(repository, uso, gerador, VERSAO, LIMITE, LIMITE_DIARIO);
        usuarioId = UUID.randomUUID();
    }

    private ConsentimentoProvador consentimento(String versao, LocalDateTime revogadoEm) {
        ConsentimentoProvador c = new ConsentimentoProvador();
        c.setUsuarioId(usuarioId);
        c.setVersao(versao);
        c.setAceitoEm(LocalDateTime.now().minusDays(1));
        c.setRevogadoEm(revogadoEm);
        return c;
    }

    private void comVagaNoDia() {
        when(uso.reservar(eq(usuarioId), any(LocalDate.class), eq(LIMITE_DIARIO))).thenReturn(1);
    }

    private static MockMultipartFile foto(String parte, byte[] bytes) {
        return new MockMultipartFile(parte, parte + ".jpg", "image/jpeg", bytes);
    }

    @Test
    @DisplayName("Sem registro, o consentimento aparece como não aceito")
    void consultarSemRegistro() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        ConsentimentoProvadorResponse resposta = service.consultarConsentimento(usuarioId);

        assertFalse(resposta.ativo());
        assertNull(resposta.versao());
    }

    @Test
    @DisplayName("Aceitar na versão atual cria o consentimento ativo")
    void aceitarCriaConsentimento() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());
        when(repository.save(any(ConsentimentoProvador.class))).thenAnswer(i -> i.getArgument(0));

        ConsentimentoProvadorResponse resposta = service.aceitarConsentimento(usuarioId, VERSAO);

        ArgumentCaptor<ConsentimentoProvador> salvo = ArgumentCaptor.forClass(ConsentimentoProvador.class);
        verify(repository).save(salvo.capture());
        assertEquals(usuarioId, salvo.getValue().getUsuarioId());
        assertEquals(VERSAO, salvo.getValue().getVersao());
        assertNotNull(salvo.getValue().getAceitoEm());
        assertTrue(resposta.ativo());
    }

    @Test
    @DisplayName("Aceitar uma versão antiga do termo é recusado")
    void aceitarVersaoAntiga() {
        assertThrows(RegraDeNegocioException.class, () -> service.aceitarConsentimento(usuarioId, "v0"));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Aceitar de novo depois de revogar reativa o mesmo registro")
    void aceitarDepoisDeRevogar() {
        ConsentimentoProvador revogado = consentimento(VERSAO, LocalDateTime.now().minusHours(1));
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(revogado));
        when(repository.save(any(ConsentimentoProvador.class))).thenAnswer(i -> i.getArgument(0));

        ConsentimentoProvadorResponse resposta = service.aceitarConsentimento(usuarioId, VERSAO);

        assertNull(revogado.getRevogadoEm());
        assertTrue(resposta.ativo());
    }

    @Test
    @DisplayName("Revogar marca a data e o consentimento deixa de valer")
    void revogar() {
        ConsentimentoProvador ativo = consentimento(VERSAO, null);
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(ativo));
        when(repository.save(any(ConsentimentoProvador.class))).thenAnswer(i -> i.getArgument(0));

        ConsentimentoProvadorResponse resposta = service.revogarConsentimento(usuarioId);

        assertNotNull(ativo.getRevogadoEm());
        assertFalse(resposta.ativo());
    }

    @Test
    @DisplayName("Revogar sem registro não falha")
    void revogarSemRegistro() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());

        assertFalse(service.revogarConsentimento(usuarioId).ativo());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Gerar sem consentimento não chama o gerador")
    void gerarSemConsentimento() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());
        MockMultipartFile cliente = foto("cliente", JPEG);
        MockMultipartFile referencia = foto("referencia", JPEG);

        assertThrows(ConsentimentoProvadorNecessarioException.class,
                () -> service.gerar(usuarioId, cliente, referencia));

        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Gerar com consentimento revogado não chama o gerador")
    void gerarComConsentimentoRevogado() {
        when(repository.findByUsuarioId(usuarioId))
                .thenReturn(Optional.of(consentimento(VERSAO, LocalDateTime.now())));
        MockMultipartFile cliente = foto("cliente", JPEG);
        MockMultipartFile referencia = foto("referencia", JPEG);

        assertThrows(ConsentimentoProvadorNecessarioException.class,
                () -> service.gerar(usuarioId, cliente, referencia));

        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Gerar com o termo aceito em versão antiga exige aceitar de novo")
    void gerarComVersaoAntiga() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento("v0", null)));
        MockMultipartFile cliente = foto("cliente", JPEG);
        MockMultipartFile referencia = foto("referencia", JPEG);

        assertThrows(ConsentimentoProvadorNecessarioException.class,
                () -> service.gerar(usuarioId, cliente, referencia));
    }

    @Test
    @DisplayName("Prévia em PNG vai como data URI PNG")
    void gerarPreviaPng() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        comVagaNoDia();
        when(gerador.gerar(JPEG, PNG)).thenReturn(PNG);

        GeracaoProvadorResponse resposta = service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", PNG));

        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG), resposta.imagemUri());
        assertNotNull(resposta.geradoEm());
    }

    @Test
    @DisplayName("Prévia em JPEG vai como data URI JPEG, não como PNG")
    void gerarPreviaJpeg() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        comVagaNoDia();
        when(gerador.gerar(JPEG, PNG)).thenReturn(JPEG);

        GeracaoProvadorResponse resposta = service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", PNG));

        assertEquals("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG), resposta.imagemUri());
        verify(uso, never()).devolver(any(), any());
    }

    @Test
    @DisplayName("Prévia em formato desconhecido vira erro de geração")
    void gerarPreviaFormatoDesconhecido() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        comVagaNoDia();
        when(gerador.gerar(JPEG, PNG)).thenReturn(new byte[]{1, 2, 3, 4});
        MockMultipartFile cliente = foto("cliente", JPEG);
        MockMultipartFile referencia = foto("referencia", PNG);

        assertThrows(GeracaoProvadorException.class,
                () -> service.gerar(usuarioId, cliente, referencia));
    }

    @Test
    @DisplayName("Foto ausente ou vazia é recusada")
    void fotoAusente() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        MockMultipartFile clienteVazia = foto("cliente", new byte[0]);
        MockMultipartFile referencia = foto("referencia", JPEG);

        assertThrows(RegraDeNegocioException.class, () -> service.gerar(usuarioId, null, referencia));
        assertThrows(RegraDeNegocioException.class, () -> service.gerar(usuarioId, clienteVazia, referencia));

        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Arquivo que não é JPG nem PNG é recusado, mesmo com tipo de imagem informado")
    void formatoInvalido() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        MockMultipartFile cliente = foto("cliente", "isto nao e uma imagem".getBytes());
        MockMultipartFile referencia = foto("referencia", JPEG);

        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, cliente, referencia));

        assertTrue(erro.getMessage().contains("JPG ou PNG"));
        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Foto acima do limite é recusada")
    void fotoGrande() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        byte[] grande = new byte[(int) LIMITE + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);
        MockMultipartFile cliente = foto("cliente", grande);
        MockMultipartFile referencia = foto("referencia", JPEG);

        assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, cliente, referencia));

        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("No limite do dia, recusa sem chamar o gerador")
    void limiteDoDia() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        when(uso.reservar(eq(usuarioId), any(LocalDate.class), eq(LIMITE_DIARIO))).thenReturn(0);

        LimiteProvadorException erro = assertThrows(LimiteProvadorException.class,
                () -> service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", JPEG)));

        assertTrue(erro.getMessage().contains("5 prévias"));
        verify(gerador, never()).gerar(any(), any());
        verify(uso, never()).devolver(any(), any());
    }

    @Test
    @DisplayName("O dia do limite é o do horário de Brasília")
    void diaDoLimite() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        comVagaNoDia();
        when(gerador.gerar(JPEG, JPEG)).thenReturn(JPEG);
        LocalDate antes = LocalDate.now(KiraTimeZone.DEFAULT);

        service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", JPEG));

        ArgumentCaptor<LocalDate> dia = ArgumentCaptor.forClass(LocalDate.class);
        verify(uso).reservar(eq(usuarioId), dia.capture(), eq(LIMITE_DIARIO));
        assertFalse(dia.getValue().isBefore(antes));
        assertFalse(dia.getValue().isAfter(LocalDate.now(KiraTimeZone.DEFAULT)));
    }

    @Test
    @DisplayName("Falha no gerador devolve a tentativa")
    void falhaDevolveATentativa() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        comVagaNoDia();
        when(gerador.gerar(JPEG, JPEG)).thenThrow(new GeracaoProvadorException("fora do ar"));

        assertThrows(GeracaoProvadorException.class,
                () -> service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", JPEG)));

        verify(uso).devolver(eq(usuarioId), any(LocalDate.class));
    }

    @Test
    @DisplayName("Prévia em formato desconhecido devolve a tentativa")
    void formatoDesconhecidoDevolveATentativa() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        comVagaNoDia();
        when(gerador.gerar(JPEG, JPEG)).thenReturn(new byte[]{1, 2, 3, 4});

        assertThrows(GeracaoProvadorException.class,
                () -> service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", JPEG)));

        verify(uso).devolver(eq(usuarioId), any(LocalDate.class));
    }

    @Test
    @DisplayName("Sem consentimento ou com foto inválida, a tentativa não é contada")
    void recusaNaoConta() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.empty());
        assertThrows(ConsentimentoProvadorNecessarioException.class,
                () -> service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", JPEG)));

        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, foto("cliente", "texto".getBytes()), foto("referencia", JPEG)));

        verify(uso, never()).reservar(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Revogar de novo mantém a data da primeira revogação")
    void revogarJaRevogadoMantemAData() {
        // Arrange
        LocalDateTime primeiraRevogacao = LocalDateTime.of(2026, 1, 10, 9, 30);
        ConsentimentoProvador revogado = consentimento(VERSAO, primeiraRevogacao);
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(revogado));
        when(repository.save(any(ConsentimentoProvador.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        ConsentimentoProvadorResponse resposta = service.revogarConsentimento(usuarioId);

        // Assert
        assertEquals(primeiraRevogacao, revogado.getRevogadoEm());
        assertFalse(resposta.ativo());
    }

    @Test
    @DisplayName("Foto que não pode ser lida vira erro de regra de negócio, sem contar tentativa")
    void fotoIlegivel() throws IOException {
        // Arrange
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        MockMultipartFile ilegivel = new MockMultipartFile("cliente", "cliente.jpg", "image/jpeg", JPEG) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("conexão caiu no meio do envio");
            }
        };
        MockMultipartFile referencia = foto("referencia", JPEG);

        // Act
        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, ilegivel, referencia));

        // Assert
        assertEquals("Não foi possível ler a foto enviada", erro.getMessage());
        verify(uso, never()).reservar(any(), any(), anyInt());
        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("A foto de referência também tem o formato conferido")
    void referenciaComFormatoInvalido() {
        // Arrange
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        MockMultipartFile cliente = foto("cliente", JPEG);
        MockMultipartFile referencia = foto("referencia", "isto nao e uma imagem".getBytes());

        // Act
        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, cliente, referencia));

        // Assert
        assertTrue(erro.getMessage().contains("JPG ou PNG"));
        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Foto só com a assinatura de JPEG, de 4 bytes, é aceita (limite mínimo)")
    void jpegNoTamanhoMinimo() {
        // Arrange
        byte[] jpegMinimo = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        comVagaNoDia();
        when(gerador.gerar(jpegMinimo, JPEG)).thenReturn(JPEG);

        // Act
        GeracaoProvadorResponse resposta =
                service.gerar(usuarioId, foto("cliente", jpegMinimo), foto("referencia", JPEG));

        // Assert
        assertTrue(resposta.imagemUri().startsWith("data:image/jpeg;base64,"));
    }

    static Stream<Arguments> arquivosQueNaoSaoJpegNemPng() {
        return Stream.of(
                Arguments.of("JPEG com só 3 bytes", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
                Arguments.of("1º byte de JPEG errado", new byte[]{0x00, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}),
                Arguments.of("2º byte de JPEG errado", new byte[]{(byte) 0xFF, 0x00, (byte) 0xFF, (byte) 0xE0}),
                Arguments.of("3º byte de JPEG errado", new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00, (byte) 0xE0}),
                Arguments.of("PNG cortado: só a assinatura de 8 bytes", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
                Arguments.of("1º byte de PNG errado", new byte[]{0x00, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}),
                Arguments.of("2º byte de PNG errado", new byte[]{(byte) 0x89, 'X', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}),
                Arguments.of("3º byte de PNG errado", new byte[]{(byte) 0x89, 'P', 'X', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}),
                Arguments.of("4º byte de PNG errado", new byte[]{(byte) 0x89, 'P', 'N', 'X', 0x0D, 0x0A, 0x1A, 0x0A, 0}),
                Arguments.of("5º byte de PNG errado", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x00, 0x0A, 0x1A, 0x0A, 0}),
                Arguments.of("6º byte de PNG errado", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x00, 0x1A, 0x0A, 0}),
                Arguments.of("7º byte de PNG errado", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x00, 0x0A, 0}),
                Arguments.of("8º byte de PNG errado", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x00, 0}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("arquivosQueNaoSaoJpegNemPng")
    @DisplayName("Arquivo parecido com JPG ou PNG, mas fora do formato, é recusado sem contar tentativa")
    void quaseImagemERecusada(String caso, byte[] bytes) {
        // Arrange
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        MockMultipartFile cliente = foto("cliente", bytes);
        MockMultipartFile referencia = foto("referencia", JPEG);

        // Act
        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, cliente, referencia));

        // Assert
        assertTrue(erro.getMessage().contains("JPG ou PNG"), caso);
        verify(uso, never()).reservar(any(), any(), anyInt());
        verify(gerador, never()).gerar(any(), any());
    }
}
