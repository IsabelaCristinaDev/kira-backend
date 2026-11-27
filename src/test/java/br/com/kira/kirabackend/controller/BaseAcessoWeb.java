package br.com.kira.kirabackend.controller;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import br.com.kira.kirabackend.security.CustomUserDetailsService;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Montagem comum dos testes de fatia web dos controllers: o SecurityConfig, o filtro de JWT e o
 * GlobalExceptionHandler são os reais; o token é simulado no JwtTokenProvider e a conta vem do
 * CustomUserDetailsService, como no fluxo de verdade. Cada subclasse declara o próprio @WebMvcTest e @Import.
 */
abstract class BaseAcessoWeb {

    @Autowired
    protected MockMvc mockMvc;

    // Dependências do JwtAuthenticationFilter
    @MockBean
    protected JwtTokenProvider jwtTokenProvider;

    @MockBean
    protected CustomUserDetailsService customUserDetailsService;

    // Usado pelo AcessoEmpresa real (importado nas classes que conferem o dono)
    @MockBean
    protected FuncionariaRepository funcionariaRepository;

    protected static Cliente novaCliente() {
        Cliente cliente = new Cliente();
        cliente.setId(UUID.randomUUID());
        cliente.setNome("Ana");
        cliente.setEmail("cliente-" + cliente.getId() + "@kira.test");
        cliente.setAtivo(true);
        return cliente;
    }

    protected static Empresa novaEmpresa() {
        Empresa empresa = new Empresa();
        empresa.setId(UUID.randomUUID());
        empresa.setNome("Estúdio");
        empresa.setEmail("empresa-" + empresa.getId() + "@kira.test");
        empresa.setAtivo(true);
        return empresa;
    }

    /** Header "Authorization: Bearer" de um token válido da conta. */
    protected RequestPostProcessor token(Usuario usuario) {
        String token = "token-" + usuario.getId();
        when(jwtTokenProvider.tokenValido(token)).thenReturn(true);
        when(jwtTokenProvider.extrairEmail(token)).thenReturn(usuario.getEmail());
        when(jwtTokenProvider.extrairEmissao(token)).thenReturn(Instant.now());
        when(customUserDetailsService.loadUserByUsername(usuario.getEmail())).thenReturn(usuario);
        return requisicao -> {
            requisicao.addHeader("Authorization", "Bearer " + token);
            return requisicao;
        };
    }

    /** Token que o JwtTokenProvider recusa (vencido ou adulterado). */
    protected static RequestPostProcessor tokenInvalido() {
        return requisicao -> {
            requisicao.addHeader("Authorization", "Bearer token-invalido");
            return requisicao;
        };
    }

    /** Funcionária cadastrada na empresa, encontrada pelo repositório. */
    protected Funcionaria funcionariaDa(Empresa empresa) {
        Funcionaria funcionaria = new Funcionaria();
        funcionaria.setId(UUID.randomUUID());
        funcionaria.setNome("Bia");
        funcionaria.setEmpresa(empresa);
        when(funcionariaRepository.findById(funcionaria.getId())).thenReturn(Optional.of(funcionaria));
        return funcionaria;
    }

    /** Resposta no formato do GlobalExceptionHandler. */
    protected static ResultMatcher erro(int codigoHttp, String erro, String caminho) {
        return ResultMatcher.matchAll(
                status().is(codigoHttp),
                jsonPath("$.status").value(codigoHttp),
                jsonPath("$.erro").value(erro),
                jsonPath("$.mensagem").isNotEmpty(),
                jsonPath("$.path").value(caminho),
                jsonPath("$.timestamp").exists());
    }
}
