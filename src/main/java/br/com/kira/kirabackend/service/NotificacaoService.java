package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.DispositivoNotificacao;
import br.com.kira.kirabackend.dto.request.DispositivoWebRequest;
import br.com.kira.kirabackend.dto.response.AgendamentoResponse;
import br.com.kira.kirabackend.dto.response.MensagemResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.DispositivoNotificacaoRepository;
import br.com.kira.kirabackend.util.KiraTimeZone;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class NotificacaoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoService.class);

    private static final int MAXIMO_POR_USUARIO = 10;
    private static final int MAXIMO_NO_CORPO = 120;
    private static final List<Integer> DISPOSITIVO_NAO_EXISTE_MAIS = List.of(404, 410);
    private static final List<String> SERVICOS_DE_PUSH = List.of(
            "fcm.googleapis.com", "push.services.mozilla.com", "push.apple.com", "notify.windows.com");
    // O app usa o tipo para decidir o que mostrar e o que abrir no toque
    static final String TIPO_CONFIRMACAO = "AGENDAMENTO_CONFIRMADO";
    private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final DispositivoNotificacaoRepository dispositivoRepository;
    private final WebPushEnvio webPush;
    private final ObjectMapper objectMapper;

    public String chavePublica() {
        return webPush.chavePublica();
    }

    @Transactional
    public void registrar(UUID usuarioId, DispositivoWebRequest request) {
        validarEndereco(request.endpoint());
        validarChaves(request);

        // Se outra conta usou o mesmo navegador antes, ele passa a ser desta
        dispositivoRepository.salvarPeloEndereco(UUID.randomUUID(), usuarioId, request.endpoint(),
                request.p256dh(), request.auth(), LocalDateTime.now(KiraTimeZone.DEFAULT));

        removerOsMaisAntigos(usuarioId);
    }

    @Transactional
    public void remover(UUID usuarioId, String endpoint) {
        dispositivoRepository.deleteByEndpointAndUsuarioId(endpoint, usuarioId);
    }

    /** Assíncrono: a mensagem já foi salva e entregue, e o serviço de push pode demorar ou falhar. */
    @Async
    public void notificarMensagem(MensagemResponse mensagem, List<UUID> destinatarios) {
        // Sem "tipo": é o formato original, e o app trata a falta de tipo como mensagem do chat
        enviarAosUsuarios(Map.of(
                "titulo", mensagem.nomeRemetente(),
                "corpo", resumir(mensagem.conteudo()),
                "agendamentoId", mensagem.agendamentoId().toString()), destinatarios);
    }

    /** Avisa a cliente que o estúdio confirmou o horário. Assíncrono pelo mesmo motivo da mensagem. */
    @Async
    public void notificarConfirmacao(AgendamentoResponse agendamento) {
        enviarAosUsuarios(Map.of(
                "tipo", TIPO_CONFIRMACAO,
                "titulo", agendamento.nomeEmpresa(),
                "corpo", "Seu horário de " + agendamento.nomeServico() + " em "
                        + DIA_E_HORA.format(agendamento.dataHoraInicio()) + " foi confirmado.",
                "agendamentoId", agendamento.id().toString()), List.of(agendamento.clienteId()));
    }

    private void enviarAosUsuarios(Map<String, String> notificacao, List<UUID> destinatarios) {
        if (!webPush.ativo() || destinatarios.isEmpty()) {
            return;
        }
        byte[] conteudo;
        try {
            conteudo = objectMapper.writeValueAsBytes(notificacao);
        } catch (JsonProcessingException e) {
            log.warn("Não foi possível montar a notificação do agendamento {}", notificacao.get("agendamentoId"));
            return;
        }
        for (DispositivoNotificacao dispositivo : dispositivoRepository.findByUsuarioIdIn(destinatarios)) {
            enviar(dispositivo, conteudo);
        }
    }

    private void enviar(DispositivoNotificacao dispositivo, byte[] conteudo) {
        try {
            int status = webPush.enviar(
                    dispositivo.getEndpoint(), dispositivo.getP256dh(), dispositivo.getAuth(), conteudo);
            if (DISPOSITIVO_NAO_EXISTE_MAIS.contains(status)) {
                dispositivoRepository.deleteById(dispositivo.getId());
            } else if (status >= 300) {
                log.warn("Serviço de push recusou o envio ao dispositivo {}: {}", dispositivo.getId(), status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Falha ao notificar o dispositivo {}: {}", dispositivo.getId(), e.getClass().getSimpleName());
        }
    }

    private void removerOsMaisAntigos(UUID usuarioId) {
        List<DispositivoNotificacao> dispositivos = dispositivoRepository.findByUsuarioIdOrderByDataCriacaoAsc(usuarioId);
        int excesso = dispositivos.size() - MAXIMO_POR_USUARIO;
        if (excesso > 0) {
            // Direto no banco: outro registro ao mesmo tempo pode já ter removido o mesmo navegador
            dispositivoRepository.deleteAllByIdInBatch(
                    dispositivos.subList(0, excesso).stream().map(DispositivoNotificacao::getId).toList());
        }
    }

    /** O servidor faz chamadas a este endereço, então só valem os serviços de push dos navegadores. */
    private static void validarEndereco(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            String host = uri.getHost();
            boolean valido = "https".equals(uri.getScheme())
                    && uri.getUserInfo() == null
                    && uri.getPort() == -1
                    && host != null
                    && SERVICOS_DE_PUSH.stream().anyMatch(s -> host.equals(s) || host.endsWith("." + s));
            if (!valido) {
                throw new RegraDeNegocioException("Endereço de notificação inválido");
            }
        } catch (URISyntaxException e) {
            throw new RegraDeNegocioException("Endereço de notificação inválido");
        }
    }

    private static void validarChaves(DispositivoWebRequest request) {
        try {
            WebPushEnvio.lerChavePublica(Base64.getUrlDecoder().decode(request.p256dh()));
            if (Base64.getUrlDecoder().decode(request.auth()).length != 16) {
                throw new RegraDeNegocioException("Chaves de notificação inválidas");
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new RegraDeNegocioException("Chaves de notificação inválidas");
        }
    }

    private static String resumir(String texto) {
        if (texto.codePointCount(0, texto.length()) <= MAXIMO_NO_CORPO) {
            return texto;
        }
        // Conta caracteres, não metades: um emoji não pode ser cortado
        int fim = texto.offsetByCodePoints(0, Math.min(MAXIMO_NO_CORPO, texto.codePointCount(0, texto.length())));
        return texto.substring(0, fim) + "…";
    }
}
