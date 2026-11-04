package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.ConsentimentoProvador;
import br.com.kira.kirabackend.dto.response.ConsentimentoProvadorResponse;
import br.com.kira.kirabackend.dto.response.GeracaoProvadorResponse;
import br.com.kira.kirabackend.exception.ConsentimentoProvadorNecessarioException;
import br.com.kira.kirabackend.exception.GeracaoProvadorException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.ConsentimentoProvadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProvadorServiceTest {

    private static final String VERSAO = "v1";
    private static final long LIMITE = 1024;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};

    @Mock
    private ConsentimentoProvadorRepository repository;

    @Mock
    private GeradorDeImagem gerador;

    private ProvadorService service;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        service = new ProvadorService(repository, gerador, VERSAO, LIMITE);
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

        assertThrows(ConsentimentoProvadorNecessarioException.class,
                () -> service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", JPEG)));

        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Gerar com consentimento revogado não chama o gerador")
    void gerarComConsentimentoRevogado() {
        when(repository.findByUsuarioId(usuarioId))
                .thenReturn(Optional.of(consentimento(VERSAO, LocalDateTime.now())));

        assertThrows(ConsentimentoProvadorNecessarioException.class,
                () -> service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", JPEG)));

        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Gerar com o termo aceito em versão antiga exige aceitar de novo")
    void gerarComVersaoAntiga() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento("v0", null)));

        assertThrows(ConsentimentoProvadorNecessarioException.class,
                () -> service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", JPEG)));
    }

    @Test
    @DisplayName("Prévia em PNG vai como data URI PNG")
    void gerarPreviaPng() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        when(gerador.gerar(JPEG, PNG)).thenReturn(PNG);

        GeracaoProvadorResponse resposta = service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", PNG));

        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(PNG), resposta.imagemUri());
        assertNotNull(resposta.geradoEm());
    }

    @Test
    @DisplayName("Prévia em JPEG vai como data URI JPEG, não como PNG")
    void gerarPreviaJpeg() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        when(gerador.gerar(JPEG, PNG)).thenReturn(JPEG);

        GeracaoProvadorResponse resposta = service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", PNG));

        assertEquals("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG), resposta.imagemUri());
    }

    @Test
    @DisplayName("Prévia em formato desconhecido vira erro de geração")
    void gerarPreviaFormatoDesconhecido() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        when(gerador.gerar(JPEG, PNG)).thenReturn(new byte[]{1, 2, 3, 4});

        assertThrows(GeracaoProvadorException.class,
                () -> service.gerar(usuarioId, foto("cliente", JPEG), foto("referencia", PNG)));
    }

    @Test
    @DisplayName("Foto ausente ou vazia é recusada")
    void fotoAusente() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));

        assertThrows(RegraDeNegocioException.class, () -> service.gerar(usuarioId, null, foto("referencia", JPEG)));
        assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, foto("cliente", new byte[0]), foto("referencia", JPEG)));

        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Arquivo que não é JPG nem PNG é recusado, mesmo com tipo de imagem informado")
    void formatoInvalido() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        byte[] texto = "isto nao e uma imagem".getBytes();

        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, foto("cliente", texto), foto("referencia", JPEG)));

        assertTrue(erro.getMessage().contains("JPG ou PNG"));
        verify(gerador, never()).gerar(any(), any());
    }

    @Test
    @DisplayName("Foto acima do limite é recusada")
    void fotoGrande() {
        when(repository.findByUsuarioId(usuarioId)).thenReturn(Optional.of(consentimento(VERSAO, null)));
        byte[] grande = new byte[(int) LIMITE + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);

        assertThrows(RegraDeNegocioException.class,
                () -> service.gerar(usuarioId, foto("cliente", grande), foto("referencia", JPEG)));

        verify(gerador, never()).gerar(any(), any());
    }
}
