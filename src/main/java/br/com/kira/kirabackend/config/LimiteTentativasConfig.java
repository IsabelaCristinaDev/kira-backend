package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.security.LimiteDeTentativas;
import br.com.kira.kirabackend.security.ProtecaoLogin;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Monta o limite de tentativas com o relógio e a pausa de verdade. Os testes montam as mesmas classes
 * com um relógio falso e uma pausa que não espera, por isso elas recebem essas peças pelo construtor.
 */
@Configuration
@EnableConfigurationProperties(LimiteTentativasPropriedades.class)
public class LimiteTentativasConfig {

    @Bean
    public LimiteDeTentativas limiteDeTentativas(LimiteTentativasPropriedades propriedades) {
        return new LimiteDeTentativas(propriedades, Ticker.systemTicker());
    }

    @Bean
    public ProtecaoLogin.Pausa pausaDoLogin() {
        return tempo -> {
            try {
                Thread.sleep(tempo.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
