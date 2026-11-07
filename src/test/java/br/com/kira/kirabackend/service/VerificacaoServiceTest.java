package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Admin;
import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.VerificacaoIdentidade;
import br.com.kira.kirabackend.domain.enums.DecisaoVerificacaoIa;
import br.com.kira.kirabackend.domain.enums.StatusVerificacao;
import br.com.kira.kirabackend.domain.enums.TipoDocumentoIdentidade;
import br.com.kira.kirabackend.domain.enums.TipoUsuario;
import br.com.kira.kirabackend.dto.response.VerificacaoResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.exception.VerificacaoIndisponivelException;
import br.com.kira.kirabackend.repository.VerificacaoIdentidadeRepository;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Imagem;
import br.com.kira.kirabackend.service.VerificadorDeIdentidade.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificacaoServiceTest {

    private static final String VERSAO = "v1";
    private static final long LIMITE_BYTES = 1024;
    private static final int LIMITE_TENTATIVAS = 3;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x01};

    @Mock
    private VerificacaoIdentidadeRepository repository;

    @Mock
    private VerificadorDeIdentidade verificador;

    private VerificacaoService service;
    private Cliente cliente;

    @BeforeEach
    void setUp() {
        service = new VerificacaoService(repository, verificador, VERSAO, LIMITE_BYTES, LIMITE_TENTATIVAS);
        cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setCpf("12345678900");
    }

    private static MockMultipartFile foto(String parte, byte[] bytes) {
        return new MockMultipartFile(parte, parte + ".jpg", "image/jpeg", bytes);
    }

    private VerificacaoResponse enviarCnh() {
        return service.enviar(cliente, foto("document", JPEG), null, foto("selfie", PNG), "CNH", true);
    }

    private void semTentativaAnterior() {
        lenient().when(repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(cliente.getId())).thenReturn(Optional.empty());
    }

    private void tentativasUsadas(long usadas) {
        lenient().when(repository.countByUsuarioIdAndStatusNot(cliente.getId(), StatusVerificacao.PENDING)).thenReturn(usadas);
    }

    private void salvarDevolveOMesmo() {
        lenient().when(repository.save(any(VerificacaoIdentidade.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void servicoResponde(DecisaoVerificacaoIa decisao, List<String> motivos) {
        when(verificador.verificar(any(), any(), any(), any(), any())).thenReturn(new Resultado(
                decisao, motivos, new BigDecimal("0.87"), new BigDecimal("0.93"), "req-1"));
    }

    private static VerificacaoIdentidade tentativa(StatusVerificacao status) {
        VerificacaoIdentidade verificacao = new VerificacaoIdentidade();
        verificacao.setStatus(status);
        verificacao.setMotivos(new ArrayList<>(List.of("FACE_MISMATCH")));
        return verificacao;
    }

    // ---- consultar ----

    @Test
    @DisplayName("Consultar sem nenhuma tentativa devolve status nulo e todas as tentativas")
    void consultarSemTentativa() {
        semTentativaAnterior();
        tentativasUsadas(0);

        VerificacaoResponse resposta = service.consultar(cliente.getId());

        assertNull(resposta.status());
        assertTrue(resposta.motivos().isEmpty());
        assertEquals(LIMITE_TENTATIVAS, resposta.tentativasRestantes());
    }

    @Test
    @DisplayName("Consultar devolve o status e os motivos da tentativa mais recente")
    void consultarComTentativa() {
        when(repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(cliente.getId()))
                .thenReturn(Optional.of(tentativa(StatusVerificacao.REJECTED)));
        tentativasUsadas(1);

        VerificacaoResponse resposta = service.consultar(cliente.getId());

        assertEquals(StatusVerificacao.REJECTED, resposta.status());
        assertEquals(List.of("FACE_MISMATCH"), resposta.motivos());
        assertEquals(2, resposta.tentativasRestantes());
    }

    @Test
    @DisplayName("Tentativas restantes nunca ficam negativas")
    void tentativasNaoFicamNegativas() {
        semTentativaAnterior();
        tentativasUsadas(5);

        assertEquals(0, service.consultar(cliente.getId()).tentativasRestantes());
    }

    // ---- enviar: caminho feliz ----

    @ParameterizedTest(name = "Decisão {0} vira status {1}")
    @CsvSource({"APPROVED, VERIFIED", "REVIEW, IN_REVIEW", "REJECTED, REJECTED"})
    void aplicaDecisaoDoServico(DecisaoVerificacaoIa decisao, StatusVerificacao esperado) {
        semTentativaAnterior();
        tentativasUsadas(0);
        salvarDevolveOMesmo();
        servicoResponde(decisao, List.of("LOW_IMAGE_QUALITY"));

        VerificacaoResponse resposta = enviarCnh();

        assertEquals(esperado, resposta.status());
        assertEquals(List.of("LOW_IMAGE_QUALITY"), resposta.motivos());
    }

    @Test
    @DisplayName("Grava consentimento, tipo e o resultado do serviço, sem imagem")
    void gravaResultado() {
        semTentativaAnterior();
        tentativasUsadas(0);
        salvarDevolveOMesmo();
        servicoResponde(DecisaoVerificacaoIa.APPROVED, List.of());

        enviarCnh();

        ArgumentCaptor<VerificacaoIdentidade> salvo = ArgumentCaptor.forClass(VerificacaoIdentidade.class);
        verify(repository, atLeastOnce()).save(salvo.capture());
        VerificacaoIdentidade verificacao = salvo.getValue();
        assertSame(cliente, verificacao.getUsuario());
        assertEquals(TipoUsuario.CLIENTE, verificacao.getTipoUsuario());
        assertEquals(TipoDocumentoIdentidade.CNH, verificacao.getTipoDocumento());
        assertEquals(VERSAO, verificacao.getVersaoConsentimento());
        assertNotNull(verificacao.getConsentimentoAceitoEm());
        assertEquals(StatusVerificacao.VERIFIED, verificacao.getStatus());
        assertEquals(new BigDecimal("0.87"), verificacao.getSimilaridade());
        assertEquals(new BigDecimal("0.93"), verificacao.getConfiancaOcr());
        assertEquals("req-1", verificacao.getRequestId());
    }

    @Test
    @DisplayName("Manda ao serviço as imagens com o formato lido dos bytes e o CPF do cadastro")
    void mandaImagensECpf() {
        semTentativaAnterior();
        tentativasUsadas(0);
        salvarDevolveOMesmo();
        servicoResponde(DecisaoVerificacaoIa.APPROVED, List.of());

        // O app diz que as duas são JPEG; a selfie na verdade é PNG
        enviarCnh();

        ArgumentCaptor<Imagem> documento = ArgumentCaptor.forClass(Imagem.class);
        ArgumentCaptor<Imagem> selfie = ArgumentCaptor.forClass(Imagem.class);
        verify(verificador).verificar(documento.capture(), isNull(), selfie.capture(), eq(TipoDocumentoIdentidade.CNH), eq("12345678900"));
        assertEquals(MediaType.IMAGE_JPEG, documento.getValue().tipo());
        assertEquals(MediaType.IMAGE_PNG, selfie.getValue().tipo());
    }

    @Test
    @DisplayName("Com o verso do documento, manda as três imagens ao serviço")
    void mandaOVerso() {
        semTentativaAnterior();
        tentativasUsadas(0);
        salvarDevolveOMesmo();
        servicoResponde(DecisaoVerificacaoIa.APPROVED, List.of());

        service.enviar(cliente, foto("document", JPEG), foto("document_back", PNG), foto("selfie", JPEG), "RG", true);

        ArgumentCaptor<Imagem> verso = ArgumentCaptor.forClass(Imagem.class);
        verify(verificador).verificar(any(), verso.capture(), any(), eq(TipoDocumentoIdentidade.RG), any());
        assertEquals(new Imagem(PNG, MediaType.IMAGE_PNG), verso.getValue());
    }

    @Test
    @DisplayName("Verso vazio conta como não enviado")
    void versoVazioNaoVai() {
        semTentativaAnterior();
        tentativasUsadas(0);
        salvarDevolveOMesmo();
        servicoResponde(DecisaoVerificacaoIa.APPROVED, List.of());

        service.enviar(cliente, foto("document", JPEG), foto("document_back", new byte[0]), foto("selfie", JPEG), "CNH", true);

        verify(verificador).verificar(any(), isNull(), any(), any(), any());
    }

    @Test
    @DisplayName("Tipo de documento aceita minúsculas e espaços")
    void tipoDocumentoFlexivel() {
        semTentativaAnterior();
        tentativasUsadas(0);
        salvarDevolveOMesmo();
        servicoResponde(DecisaoVerificacaoIa.APPROVED, List.of());

        service.enviar(cliente, foto("document", JPEG), null, foto("selfie", JPEG), " rg ", true);

        verify(verificador).verificar(any(), isNull(), any(), eq(TipoDocumentoIdentidade.RG), any());
    }

    @Test
    @DisplayName("Empresa é verificada como EMPRESA; sem CPF (CNPJ), o CPF esperado vai nulo")
    void empresaSemCpf() {
        Empresa empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        when(repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(empresa.getId())).thenReturn(Optional.empty());
        when(repository.countByUsuarioIdAndStatusNot(empresa.getId(), StatusVerificacao.PENDING)).thenReturn(0L);
        salvarDevolveOMesmo();
        servicoResponde(DecisaoVerificacaoIa.APPROVED, List.of());

        service.enviar(empresa, foto("document", JPEG), null, foto("selfie", JPEG), "CNH", true);

        ArgumentCaptor<VerificacaoIdentidade> salvo = ArgumentCaptor.forClass(VerificacaoIdentidade.class);
        verify(repository, atLeastOnce()).save(salvo.capture());
        assertEquals(TipoUsuario.EMPRESA, salvo.getValue().getTipoUsuario());
        verify(verificador).verificar(any(), isNull(), any(), any(), eq(null));
    }

    @Test
    @DisplayName("Depois de uma reprovação, ainda com tentativas, pode enviar de novo em uma linha nova")
    void novaTentativaDepoisDeReprovar() {
        VerificacaoIdentidade reprovada = tentativa(StatusVerificacao.REJECTED);
        when(repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(cliente.getId())).thenReturn(Optional.of(reprovada));
        tentativasUsadas(1);
        salvarDevolveOMesmo();
        servicoResponde(DecisaoVerificacaoIa.APPROVED, List.of());

        enviarCnh();

        ArgumentCaptor<VerificacaoIdentidade> salvo = ArgumentCaptor.forClass(VerificacaoIdentidade.class);
        verify(repository, atLeastOnce()).save(salvo.capture());
        assertTrue(salvo.getAllValues().stream().noneMatch(v -> v == reprovada));
        assertEquals(StatusVerificacao.REJECTED, reprovada.getStatus());
    }

    @Test
    @DisplayName("Tentativa que ficou PENDING é reaproveitada, sem criar outra linha")
    void reaproveitaPendente() {
        VerificacaoIdentidade pendente = tentativa(StatusVerificacao.PENDING);
        when(repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(cliente.getId())).thenReturn(Optional.of(pendente));
        tentativasUsadas(0);
        salvarDevolveOMesmo();
        servicoResponde(DecisaoVerificacaoIa.REVIEW, List.of());

        enviarCnh();

        ArgumentCaptor<VerificacaoIdentidade> salvo = ArgumentCaptor.forClass(VerificacaoIdentidade.class);
        verify(repository, atLeastOnce()).save(salvo.capture());
        assertTrue(salvo.getAllValues().stream().allMatch(v -> v == pendente));
        assertEquals(StatusVerificacao.IN_REVIEW, pendente.getStatus());
    }

    // ---- enviar: falha do serviço ----

    @Test
    @DisplayName("Serviço indisponível: a tentativa fica PENDING e o erro sobe para virar 503")
    void servicoIndisponivel() {
        semTentativaAnterior();
        tentativasUsadas(0);
        salvarDevolveOMesmo();
        when(verificador.verificar(any(), any(), any(), any(), any()))
                .thenThrow(new VerificacaoIndisponivelException("fora do ar", new RuntimeException()));

        assertThrows(VerificacaoIndisponivelException.class, this::enviarCnh);

        ArgumentCaptor<VerificacaoIdentidade> salvo = ArgumentCaptor.forClass(VerificacaoIdentidade.class);
        verify(repository).save(salvo.capture());
        assertEquals(StatusVerificacao.PENDING, salvo.getValue().getStatus());
    }

    // ---- enviar: regras violadas ----

    @ParameterizedTest(name = "Consentimento {0} é recusado")
    @NullSource
    @ValueSource(booleans = false)
    void semConsentimento(Boolean consentimento) {
        MockMultipartFile documento = foto("document", JPEG);
        MockMultipartFile selfie = foto("selfie", JPEG);

        assertThrows(RegraDeNegocioException.class,
                () -> service.enviar(cliente, documento, null, selfie, "CNH", consentimento));

        verifyNoInteractions(repository, verificador);
    }

    @Test
    @DisplayName("Conta de administrador não faz verificação")
    void adminNaoVerifica() {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());

        MockMultipartFile documento = foto("document", JPEG);
        MockMultipartFile selfie = foto("selfie", JPEG);

        assertThrows(RegraDeNegocioException.class,
                () -> service.enviar(admin, documento, null, selfie, "CNH", true));

        verifyNoInteractions(repository, verificador);
    }

    @ParameterizedTest(name = "Tipo de documento \"{0}\" é recusado")
    @NullSource
    @ValueSource(strings = {"", "  ", "PASSAPORTE"})
    void tipoDocumentoInvalido(String tipo) {
        MockMultipartFile documento = foto("document", JPEG);
        MockMultipartFile selfie = foto("selfie", JPEG);

        assertThrows(RegraDeNegocioException.class,
                () -> service.enviar(cliente, documento, null, selfie, tipo, true));

        verifyNoInteractions(repository, verificador);
    }

    @Test
    @DisplayName("Sem a foto do documento ou sem a selfie é recusado")
    void semFoto() {
        MockMultipartFile documento = foto("document", JPEG);
        MockMultipartFile selfie = foto("selfie", JPEG);
        MockMultipartFile vazia = foto("selfie", new byte[0]);

        assertThrows(RegraDeNegocioException.class, () -> service.enviar(cliente, null, null, selfie, "CNH", true));
        assertThrows(RegraDeNegocioException.class, () -> service.enviar(cliente, documento, null, vazia, "CNH", true));

        verifyNoInteractions(repository, verificador);
    }

    @Test
    @DisplayName("Verso grande demais ou fora de JPG e PNG é recusado como as outras fotos")
    void versoInvalido() {
        byte[] grande = new byte[(int) LIMITE_BYTES + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0};
        MockMultipartFile documento = foto("document", JPEG);
        MockMultipartFile versoGrande = foto("document_back", grande);
        MockMultipartFile versoGif = foto("document_back", gif);
        MockMultipartFile selfie = foto("selfie", JPEG);

        assertThrows(RegraDeNegocioException.class,
                () -> service.enviar(cliente, documento, versoGrande, selfie, "RG", true));
        assertThrows(RegraDeNegocioException.class,
                () -> service.enviar(cliente, documento, versoGif, selfie, "RG", true));

        verifyNoInteractions(repository, verificador);
    }

    @Test
    @DisplayName("Foto acima do tamanho máximo é recusada")
    void fotoGrande() {
        byte[] grande = new byte[(int) LIMITE_BYTES + 1];
        System.arraycopy(JPEG, 0, grande, 0, JPEG.length);

        MockMultipartFile documento = foto("document", grande);
        MockMultipartFile selfie = foto("selfie", JPEG);

        assertThrows(RegraDeNegocioException.class,
                () -> service.enviar(cliente, documento, null, selfie, "CNH", true));

        verifyNoInteractions(repository, verificador);
    }

    @Test
    @DisplayName("Foto que não é JPG nem PNG é recusada, mesmo dizendo que é image/jpeg")
    void formatoInvalido() {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0};

        MockMultipartFile documento = foto("document", gif);
        MockMultipartFile selfie = foto("selfie", JPEG);

        assertThrows(RegraDeNegocioException.class,
                () -> service.enviar(cliente, documento, null, selfie, "CNH", true));

        verifyNoInteractions(repository, verificador);
    }

    @ParameterizedTest(name = "Com a última tentativa {0} não pode enviar de novo")
    @EnumSource(value = StatusVerificacao.class, names = {"VERIFIED", "IN_REVIEW"})
    void naoReenviaVerificadaOuEmAnalise(StatusVerificacao status) {
        when(repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(cliente.getId()))
                .thenReturn(Optional.of(tentativa(status)));

        assertThrows(RegraDeNegocioException.class, this::enviarCnh);

        verify(repository, never()).save(any());
        verifyNoInteractions(verificador);
    }

    @Test
    @DisplayName("Sem tentativas restantes é recusado antes de chamar o serviço")
    void semTentativas() {
        when(repository.findFirstByUsuarioIdOrderByDataCriacaoDesc(cliente.getId()))
                .thenReturn(Optional.of(tentativa(StatusVerificacao.REJECTED)));
        tentativasUsadas(LIMITE_TENTATIVAS);

        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class, this::enviarCnh);

        assertEquals("Você usou todas as 3 tentativas de verificação", erro.getMessage());
        verify(repository, never()).save(any());
        verifyNoInteractions(verificador);
    }
}
