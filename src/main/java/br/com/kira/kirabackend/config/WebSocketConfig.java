package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.security.WebSocketAutenticacaoInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Chat em tempo real: o app assina a conversa em /topic/agendamentos/{id}/mensagens. O envio continua
 * pelo POST de /api/mensagens, que tem as regras; o WebSocket só avisa quem está com a conversa aberta.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAutenticacaoInterceptor autenticacaoInterceptor;

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
        registry.addEndpoint("/ws").setAllowedOrigins(SecurityConfig.ORIGENS_PERMITIDAS.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(autenticacaoInterceptor);
    }
}
