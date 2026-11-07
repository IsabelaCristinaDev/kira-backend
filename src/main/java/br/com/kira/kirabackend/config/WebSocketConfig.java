package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.security.WebSocketAutenticacaoInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * Chat em tempo real: o app assina a conversa em /topic/agendamentos/{id}/mensagens. O envio continua
 * pelo POST de /api/mensagens, que tem as regras; o WebSocket só avisa quem está com a conversa aberta.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Servidor e cliente avisam que estão vivos a cada 10 s, o padrão do stompjs
    private static final long[] HEARTBEAT_MS = {10_000, 10_000};

    private final WebSocketAutenticacaoInterceptor autenticacaoInterceptor;
    private final TaskScheduler heartbeatScheduler;
    // As mesmas origens do CORS da API (kira.cors.origens), com a mesma validação
    private final List<String> origensPermitidas;

    // @Lazy porque o scheduler é criado por esta própria classe
    public WebSocketConfig(
            WebSocketAutenticacaoInterceptor autenticacaoInterceptor,
            @Lazy @Qualifier("taskScheduler") TaskScheduler heartbeatScheduler,
            @Value("${kira.cors.origens}") List<String> origensPermitidas) {
        this.autenticacaoInterceptor = autenticacaoInterceptor;
        this.heartbeatScheduler = heartbeatScheduler;
        this.origensPermitidas = SecurityConfig.validarOrigens(origensPermitidas);
    }

    // O broker cria executores próprios, e o Spring Boot deixa de criar os padrão ao vê-los. Sem estes dois,
    // cada e-mail @Async abre uma thread nova, sem limite, e o job de lembrete roda nas threads do broker.
    @Bean
    public ThreadPoolTaskExecutor taskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(origensPermitidas.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Sem heartbeat, o app não percebe uma conexão que caiu sem avisar (troca de Wi-Fi, por exemplo)
        registry.enableSimpleBroker("/topic")
                .setTaskScheduler(heartbeatScheduler)
                .setHeartbeatValue(HEARTBEAT_MS);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(autenticacaoInterceptor);
    }
}
