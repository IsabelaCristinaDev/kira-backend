package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** Origens do app web (kira.cors.origens), já validadas. Valem só para o CORS da API. */
    private final List<String> origensPermitidas;

    // Construtor escrito à mão: o Lombok não copia o @Value para o parâmetro
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${kira.cors.origens}") List<String> origensPermitidas) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.origensPermitidas = validarOrigens(origensPermitidas);
    }

    @Bean
    @SuppressWarnings("java:S4502") // CSRF desligado de propósito: ver comentário no .csrf()
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF protege autenticação via cookie; esta API é stateless e autentica
                // exclusivamente via header "Authorization: Bearer", nunca por cookie,
                // então não há vetor de CSRF a mitigar aqui.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sem token, ou com token vencido, é 401: é o sinal para o app renovar a sessão.
                // O 403 fica para quem está logado, mas não tem permissão.
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/registro/cliente").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/registro/empresa").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/studios/**").permitAll()
                        // As avaliações feitas pela própria pessoa dependem de quem está logado (a regra abaixo libera o resto)
                        .requestMatchers(HttpMethod.GET, "/api/avaliacoes/minhas").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/avaliacoes/cliente/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/avaliacoes/**").permitAll()
                        // O painel tem faturamento e clientes: exige login (a regra abaixo libera o resto de /api/empresas)
                        .requestMatchers(HttpMethod.GET, "/api/empresas/*/dashboard").authenticated()
                        // O perfil tem e-mail e documentos: também exige login
                        .requestMatchers(HttpMethod.GET, "/api/empresas/*/perfil").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/empresas/**").permitAll()
                        // A foto de perfil aparece na vitrine, sem login. O nome do arquivo é aleatório
                        .requestMatchers(HttpMethod.GET, "/api/arquivos/*").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // O navegador não manda header na abertura do WebSocket: o token é conferido
                        // no CONNECT do STOMP (WebSocketAutenticacaoInterceptor)
                        .requestMatchers("/ws").permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        // Sem filtrar por método: senão GET/PUT/DELETE caem no authenticated() e qualquer login passa
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/auth/recuperar-senha").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/redefinir-senha").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origensPermitidas);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Confere a lista na subida da aplicação: um erro aqui apareceria só como "CORS bloqueado" no navegador.
     * Tira espaços e a barra do fim, porque o navegador manda a origem sem ela.
     */
    static List<String> validarOrigens(List<String> origens) {
        List<String> validas = origens == null ? List.of() : origens.stream()
                .map(String::trim)
                .filter(origem -> !origem.isEmpty())
                .map(origem -> StringUtils.trimTrailingCharacter(origem, '/'))
                .toList();

        if (validas.isEmpty()) {
            throw new IllegalStateException("Defina KIRA_CORS_ORIGENS com o endereço do app web");
        }
        for (String origem : validas) {
            // Com allowCredentials, o Spring não aceita o curinga "*"
            if (!origem.startsWith("http://") && !origem.startsWith("https://")) {
                throw new IllegalStateException(
                        "Origem inválida em KIRA_CORS_ORIGENS: \"" + origem + "\". Use http:// ou https://");
            }
        }
        return validas;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}