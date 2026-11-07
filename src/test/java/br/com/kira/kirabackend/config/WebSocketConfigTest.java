package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.security.WebSocketAutenticacaoInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WebSocketConfigTest {

    @Test
    @DisplayName("O broker sobe com heartbeat de 10 s nos dois sentidos")
    void brokerComHeartbeat() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
                .withBean(WebSocketAutenticacaoInterceptor.class,
                        () -> Mockito.mock(WebSocketAutenticacaoInterceptor.class))
                .withUserConfiguration(WebSocketConfig.class)
                .withPropertyValues("kira.cors.origens=http://localhost:5173")
                .run(context -> {
                    SimpleBrokerMessageHandler broker = context.getBean(SimpleBrokerMessageHandler.class);
                    assertArrayEquals(new long[] {10_000, 10_000}, broker.getHeartbeatValue());
                    assertNotNull(broker.getTaskScheduler());
                });
    }

    @Test
    @DisplayName("Origem inválida na configuração impede o WebSocket de subir")
    void origemInvalidaNaoSobe() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
                .withBean(WebSocketAutenticacaoInterceptor.class,
                        () -> Mockito.mock(WebSocketAutenticacaoInterceptor.class))
                .withUserConfiguration(WebSocketConfig.class)
                .withPropertyValues("kira.cors.origens=*")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertInstanceOf(IllegalStateException.class, rootCause(context.getStartupFailure()));
                });
    }

    private static Throwable rootCause(Throwable erro) {
        Throwable causa = erro;
        while (causa.getCause() != null) {
            causa = causa.getCause();
        }
        return causa;
    }
}
