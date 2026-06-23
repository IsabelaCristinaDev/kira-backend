package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AuthenticationManager authenticationManager;

    public LoginResponse login(LoginRequest data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(
                data.email(), data.senha());
        var auth = authenticationManager.authenticate(usernamePassword);

        var usuario = usuarioRepository.findByEmail(data.email())
                .orElseThrow();

        String tipoUsuario = usuario.getClass().getSimpleName().toUpperCase();
        var token = jwtTokenProvider.gerarToken(
                (UserDetails) auth.getPrincipal(), tipoUsuario);

        return new LoginResponse(token, tipoUsuario, usuario.getNome(), usuario.getEmail());
    }

    public void registrarCliente(ClienteRegistroRequest data) {
        if (usuarioRepository.existsByEmail(data.email())) {
            throw new RuntimeException("Email já cadastrado");
        }

        Cliente cliente = new Cliente();
        cliente.setNome(data.nome());
        cliente.setEmail(data.email());
        cliente.setSenhaHash(passwordEncoder.encode(data.senha()));
        cliente.setTelefone(data.telefone());
        cliente.setCpf(data.cpf());

        usuarioRepository.save(cliente);
    }

    public void registrarEmpresa(EmpresaRegistroRequest data) {
        if (usuarioRepository.existsByEmail(data.email())) {
            throw new RuntimeException("Email já cadastrado");
        }

        Empresa empresa = new Empresa();
        empresa.setNome(data.nome());
        empresa.setEmail(data.email());
        empresa.setSenhaHash(passwordEncoder.encode(data.senha()));
        empresa.setTelefone(data.telefone());
        empresa.setCnpj(data.cnpj());
        empresa.setDescricao(data.descricao());
        empresa.setEndereco(data.endereco());

        usuarioRepository.save(empresa);
    }
}