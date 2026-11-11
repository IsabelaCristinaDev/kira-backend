package br.com.kira.kirabackend.security;

import br.com.kira.kirabackend.domain.entity.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = recuperarToken(request);

        if (token != null && jwtTokenProvider.tokenValido(token)) {
            var email = jwtTokenProvider.extrairEmail(token);
            UserDetails userDetails = buscarConta(email);
            // Conta desativada ou excluída depois do login: o token ainda é válido, mas não pode autenticar
            if (userDetails == null || !userDetails.isEnabled()) {
                filterChain.doFilter(request, response);
                return;
            }
            // Senha redefinida depois que este token foi emitido: a sessão foi encerrada
            if (userDetails instanceof Usuario usuario
                    && usuario.tokenAnteriorATrocaDeSenha(jwtTokenProvider.extrairEmissao(token))) {
                filterChain.doFilter(request, response);
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    // A conta excluída troca o e-mail, então o token antigo aponta para um e-mail que não existe mais
    private UserDetails buscarConta(String email) {
        try {
            return userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    private String recuperarToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }
}