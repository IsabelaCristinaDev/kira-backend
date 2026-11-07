package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.DispositivoNotificacao;
import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import br.com.kira.kirabackend.dto.request.DispositivoWebRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.DispositivoNotificacaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.interfaces.ECPublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {

    private static final String ENDERECO = "https://fcm.googleapis.com/fcm/send/abc";

    @Mock
    private DispositivoNotificacaoRepository dispositivoRepository;

    @Mock
    private WebPushEnvio webPush;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificacaoService service;
    private final UUID usuarioId = UUID.randomUUID();
    private String chaveValida;
    private String segredoValido;

    @BeforeEach
    void setUp() throws Exception {
        service = new NotificacaoService(dispositivoRepository, webPush, objectMapper);
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        chaveValida = base64.encodeToString(WebPushEnvio.codificarPonto((ECPublicKey) WebPushEnvio.gerarChaves().getPublic()));
        segredoValido = base64.encodeToString(new byte[16]);
    }

    private DispositivoWebRequest request(String endereco) {
        return new DispositivoWebRequest(endereco, chaveValida, segredoValido);
    }

    private DispositivoNotificacao dispositivo() {
        DispositivoNotificacao dispositivo = new DispositivoNotificacao();
        dispositivo.setId(UUID.randomUUID());
        dispositivo.setEndpoint(ENDERECO);
        dispositivo.setP256dh(chaveValida);
        dispositivo.setAuth(segredoValido);
        return dispositivo;
    }

    private MensagemResponse mensagem(String conteudo) {
        return new MensagemResponse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Ana", conteudo,
                false, LocalDateTime.now());
    }

    @Test
    @DisplayName("Registra o navegador da pessoa")
    void registra() {
        when(dispositivoRepository.findByUsuarioIdOrderByDataCriacaoAsc(usuarioId)).thenReturn(List.of());

        service.registrar(usuarioId, request(ENDERECO));

        verify(dispositivoRepository).salvarPeloEndereco(any(), eq(usuarioId), eq(ENDERECO), eq(chaveValida),
                eq(segredoValido), any());
    }

    @Test
    @DisplayName("Passando do limite por pessoa, saem os navegadores mais antigos")
    void limitePorPessoa() {
        List<DispositivoNotificacao> dispositivos = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            dispositivos.add(dispositivo());
        }
        when(dispositivoRepository.findByUsuarioIdOrderByDataCriacaoAsc(usuarioId)).thenReturn(dispositivos);

        service.registrar(usuarioId, request(ENDERECO));

        verify(dispositivoRepository).deleteAllByIdInBatch(List.of(dispositivos.get(0).getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "https://localhost/push",
            "https://169.254.169.254/latest",
            "https://exemplo.com/push",
            "https://fcm.googleapis.com.exemplo.com/push",
            "https://exemplofcm.googleapis.com/push",
            "https://usuario@fcm.googleapis.com/push",
            "https://fcm.googleapis.com:8443/push",
            "nao e um endereco",
    })
    @DisplayName("Só aceita endereços dos serviços de push dos navegadores")
    void enderecoNaoPermitido(String endereco) {
        DispositivoWebRequest req = request(endereco);

        assertThrows(RegraDeNegocioException.class, () -> service.registrar(usuarioId, req));
        verifyNoInteractions(dispositivoRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc",
            "https://updates.push.services.mozilla.com/wpush/v2/abc",
            "https://web.push.apple.com/abc",
            "https://wns2-par02p.notify.windows.com/w/?token=abc",
    })
    @DisplayName("Aceita os serviços de push do Chrome, Firefox, Safari e Edge")
    void enderecoPermitido(String endereco) {
        when(dispositivoRepository.findByUsuarioIdOrderByDataCriacaoAsc(usuarioId)).thenReturn(List.of());

        service.registrar(usuarioId, request(endereco));

        verify(dispositivoRepository).salvarPeloEndereco(any(), eq(usuarioId), eq(endereco), any(), any(), any());
    }

    @Test
    @DisplayName("Chaves fora do formato são recusadas")
    void chavesInvalidas() {
        DispositivoWebRequest chaveRuim = new DispositivoWebRequest(ENDERECO, "curta", segredoValido);
        DispositivoWebRequest segredoRuim = new DispositivoWebRequest(ENDERECO, chaveValida, "curto");

        assertThrows(RegraDeNegocioException.class, () -> service.registrar(usuarioId, chaveRuim));
        assertThrows(RegraDeNegocioException.class, () -> service.registrar(usuarioId, segredoRuim));
        verifyNoInteractions(dispositivoRepository);
    }

    @Test
    @DisplayName("Remover vale só para o navegador da própria pessoa")
    void remover() {
        service.remover(usuarioId, ENDERECO);

        verify(dispositivoRepository).deleteByEndpointAndUsuarioId(ENDERECO, usuarioId);
    }

    @Test
    @DisplayName("Envia a mensagem para cada navegador de quem recebeu")
    void enviaParaCadaNavegador() throws Exception {
        when(webPush.ativo()).thenReturn(true);
        when(dispositivoRepository.findByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of(dispositivo(), dispositivo()));
        when(webPush.enviar(anyString(), anyString(), anyString(), any())).thenReturn(201);
        MensagemResponse mensagem = mensagem("Oi");

        service.notificarMensagem(mensagem, List.of(usuarioId));

        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);
        verify(webPush, times(2)).enviar(eq(ENDERECO), anyString(), anyString(), conteudo.capture());
        JsonNode json = objectMapper.readTree(conteudo.getValue());
        assertEquals("Ana", json.get("titulo").asText());
        assertEquals("Oi", json.get("corpo").asText());
        assertEquals(mensagem.agendamentoId().toString(), json.get("agendamentoId").asText());
        verify(dispositivoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Mensagem longa vai resumida, sem cortar um emoji no meio")
    void mensagemLonga() throws Exception {
        when(webPush.ativo()).thenReturn(true);
        when(dispositivoRepository.findByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of(dispositivo()));
        when(webPush.enviar(anyString(), anyString(), anyString(), any())).thenReturn(201);

        service.notificarMensagem(mensagem("😀".repeat(500)), List.of(usuarioId));

        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);
        verify(webPush).enviar(anyString(), anyString(), anyString(), conteudo.capture());
        String corpo = objectMapper.readTree(conteudo.getValue()).get("corpo").asText();
        assertTrue(corpo.endsWith("😀…"), corpo);
        assertTrue(corpo.codePointCount(0, corpo.length()) <= 121);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 410})
    @DisplayName("Navegador que não existe mais é removido")
    void navegadorQueNaoExisteMais(int status) throws Exception {
        DispositivoNotificacao dispositivo = dispositivo();
        when(webPush.ativo()).thenReturn(true);
        when(dispositivoRepository.findByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of(dispositivo));
        when(webPush.enviar(anyString(), anyString(), anyString(), any())).thenReturn(status);

        service.notificarMensagem(mensagem("Oi"), List.of(usuarioId));

        verify(dispositivoRepository).deleteById(dispositivo.getId());
    }

    @Test
    @DisplayName("Falha em um navegador não impede os outros nem remove o navegador")
    void falhaNaoImpedeOsOutros() throws Exception {
        when(webPush.ativo()).thenReturn(true);
        when(dispositivoRepository.findByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of(dispositivo(), dispositivo()));
        when(webPush.enviar(anyString(), anyString(), anyString(), any()))
                .thenThrow(new IOException("fora do ar"))
                .thenReturn(500);

        service.notificarMensagem(mensagem("Oi"), List.of(usuarioId));

        verify(webPush, times(2)).enviar(anyString(), anyString(), anyString(), any());
        verify(dispositivoRepository, never()).deleteById(any());
    }

    private AgendamentoResponse confirmado() {
        return new AgendamentoResponse(UUID.randomUUID(), usuarioId, "Camila", UUID.randomUUID(), "Espaço Aurora",
                null, null, UUID.randomUUID(), "Corte feminino", 60, LocalDateTime.of(2026, 11, 6, 10, 0),
                LocalDateTime.of(2026, 11, 6, 11, 0), StatusAgendamento.CONFIRMADO, null, null);
    }

    @Test
    @DisplayName("Confirmação avisa os navegadores da cliente, com o estúdio, o serviço, o dia e a hora")
    void avisaConfirmacao() throws Exception {
        AgendamentoResponse agendamento = confirmado();
        when(webPush.ativo()).thenReturn(true);
        when(dispositivoRepository.findByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of(dispositivo()));
        when(webPush.enviar(anyString(), anyString(), anyString(), any())).thenReturn(201);

        service.notificarConfirmacao(agendamento);

        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);
        verify(webPush).enviar(eq(ENDERECO), anyString(), anyString(), conteudo.capture());
        JsonNode json = objectMapper.readTree(conteudo.getValue());
        assertEquals("AGENDAMENTO_CONFIRMADO", json.get("tipo").asText());
        assertEquals("Espaço Aurora", json.get("titulo").asText());
        assertEquals("Seu horário de Corte feminino em 06/11 às 10:00 foi confirmado.", json.get("corpo").asText());
        assertEquals(agendamento.id().toString(), json.get("agendamentoId").asText());
    }

    @Test
    @DisplayName("Confirmação sem chaves configuradas não consulta nem envia nada")
    void confirmacaoDesligada() {
        when(webPush.ativo()).thenReturn(false);

        service.notificarConfirmacao(confirmado());

        verifyNoInteractions(dispositivoRepository);
    }

    @Test
    @DisplayName("Mensagem do chat não leva tipo: o app trata a falta de tipo como mensagem")
    void mensagemSemTipo() throws Exception {
        when(webPush.ativo()).thenReturn(true);
        when(dispositivoRepository.findByUsuarioIdIn(List.of(usuarioId))).thenReturn(List.of(dispositivo()));
        when(webPush.enviar(anyString(), anyString(), anyString(), any())).thenReturn(201);

        service.notificarMensagem(mensagem("Oi"), List.of(usuarioId));

        ArgumentCaptor<byte[]> conteudo = ArgumentCaptor.forClass(byte[].class);
        verify(webPush).enviar(anyString(), anyString(), anyString(), conteudo.capture());
        assertFalse(objectMapper.readTree(conteudo.getValue()).has("tipo"));
    }

    @Test
    @DisplayName("Sem chaves configuradas, não consulta nem envia nada")
    void desligado() {
        when(webPush.ativo()).thenReturn(false);

        service.notificarMensagem(mensagem("Oi"), List.of(usuarioId));

        verifyNoInteractions(dispositivoRepository);
    }
}
