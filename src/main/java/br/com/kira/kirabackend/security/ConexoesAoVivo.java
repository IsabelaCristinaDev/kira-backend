package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.observer.SenhaRedefinidaEvent;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Conexões do WebSocket abertas nesta instância. O token só é conferido ao conectar, então aqui a
 * conexão é encerrada quando o token vence ou a conta é desativada, como já acontece nas rotas HTTP.
 */
@Component
public class ConexoesAoVivo {

    // O app renova o token e reconecta quando a recusa começa com "Token"; as outras recusas são definitivas
    static final String TOKEN_EXPIRADO = "Token expirado";
    static final String CONTA_DESATIVADA = "Conta desativada";
    static final String SENHA_TROCADA = "Senha trocada";

    private record Conexao(UUID usuarioId, Instant expira) {
    }

    private final Map<String, Conexao> conexoes = new ConcurrentHashMap<>();
    private final MessageChannel saidaParaClientes;
    private final UsuarioRepository usuarioRepository;

    // @Lazy porque o canal é criado pela configuração do broker, que depende do interceptor que usa esta classe
    public ConexoesAoVivo(
            @Lazy @Qualifier("clientOutboundChannel") MessageChannel saidaParaClientes,
            UsuarioRepository usuarioRepository) {
        this.saidaParaClientes = saidaParaClientes;
        this.usuarioRepository = usuarioRepository;
    }

    public void registrar(String sessaoId, UUID usuarioId, Instant expira) {
        conexoes.put(sessaoId, new Conexao(usuarioId, expira));
    }

    @EventListener
    public void aoDesconectar(SessionDisconnectEvent evento) {
        conexoes.remove(evento.getSessionId());
    }

    /**
     * Senha redefinida: o chat aberto do usuário é fechado na hora, sem esperar o token vencer.
     * AFTER_COMMIT: só depois que a troca foi gravada. Se ela falhar, ninguém é desconectado.
     * Síncrono, como os outros listeners do projeto. Vale para as conexões desta instância da API.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoRedefinirSenha(SenhaRedefinidaEvent evento) {
        conexoes.forEach((sessaoId, conexao) -> {
            if (conexao.usuarioId().equals(evento.getUsuarioId())) {
                encerrar(sessaoId, SENHA_TROCADA);
            }
        });
    }

    @Scheduled(fixedDelayString = "${kira.websocket.verificacao-ms:30000}")
    public void verificar() {
        Instant agora = Instant.now();
        conexoes.forEach((sessaoId, conexao) -> {
            if (!conexao.expira().isAfter(agora)) {
                encerrar(sessaoId, TOKEN_EXPIRADO);
            }
        });

        if (conexoes.isEmpty()) {
            return;
        }
        Set<UUID> usuarios = conexoes.values().stream().map(Conexao::usuarioId).collect(Collectors.toSet());
        Set<UUID> ativos = usuarioRepository.findIdsAtivos(usuarios);
        conexoes.forEach((sessaoId, conexao) -> {
            if (!ativos.contains(conexao.usuarioId())) {
                encerrar(sessaoId, CONTA_DESATIVADA);
            }
        });
    }

    // O Spring fecha a conexão depois de mandar um frame ERROR ao cliente
    private void encerrar(String sessaoId, String motivo) {
        conexoes.remove(sessaoId);
        StompHeaderAccessor erro = StompHeaderAccessor.create(StompCommand.ERROR);
        erro.setMessage(motivo);
        erro.setSessionId(sessaoId);
        erro.setLeaveMutable(true);
        saidaParaClientes.send(MessageBuilder.createMessage(new byte[0], erro.getMessageHeaders()));
    }
}
