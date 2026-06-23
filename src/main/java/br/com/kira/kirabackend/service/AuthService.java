package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.senha()
                )
        );

        var usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow();

        String tipoUsuario = usuario.getClass().getSimpleName().toUpperCase();
        String token = jwtTokenProvider.gerarToken(usuario.getEmail(), tipoUsuario);

        return new LoginResponse(token, tipoUsuario, usuario.getNome(), usuario.getEmail());
    }
    public void registrarCliente(ClienteRegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email já cadastrado");
        }

        Cliente cliente = new Cliente();
        cliente.setNome(request.nome());
        cliente.setEmail(request.email());
        cliente.setSenhaHash(passwordEncoder.encode(request.senha()));
        cliente.setTelefone(request.telefone());
        cliente.setCpf(request.cpf());

        usuarioRepository.save(cliente);
    }

    public void registrarEmpresa(EmpresaRegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email já cadastrado");
        }

        Empresa empresa = new Empresa();
        empresa.setNome(request.nome());
        empresa.setEmail(request.email());
        empresa.setSenhaHash(passwordEncoder.encode(request.senha()));
        empresa.setTelefone(request.telefone());
        empresa.setCnpj(request.cnpj());
        empresa.setDescricao(request.descricao());
        empresa.setEndereco(request.endereco());

        usuarioRepository.save(empresa);
    }
}