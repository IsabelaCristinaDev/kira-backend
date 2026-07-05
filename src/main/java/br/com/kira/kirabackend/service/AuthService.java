package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.request.ServicoRegistroRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AuthService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ServicoRepository servicoRepository;

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
        cliente.setDataNascimento(data.dataNascimento());
        cliente.setGenero(data.genero());
        cliente.setLatitude(data.latitude());
        cliente.setLongitude(data.longitude());

        if (data.endereco() != null) {
            Endereco endereco = new Endereco();
            endereco.setCep(data.endereco().cep());
            endereco.setLogradouro(data.endereco().logradouro());
            endereco.setNumero(data.endereco().numero());
            endereco.setComplemento(data.endereco().complemento());
            endereco.setBairro(data.endereco().bairro());
            endereco.setCidade(data.endereco().cidade());
            endereco.setEstado(data.endereco().estado());
            cliente.setEndereco(endereco);
        }

        usuarioRepository.save(cliente);
    }

    @Transactional
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
        empresa.setCpf(data.cpf());
        empresa.setDescricao(data.descricao());
        empresa.setDataNascimento(data.dataNascimento());
        empresa.setLatitude(data.latitude());
        empresa.setLongitude(data.longitude());
        empresa.setTipoEstabelecimento(data.tipoEstabelecimento());

        if (data.especialidades() != null && !data.especialidades().isEmpty()) {
            empresa.setEspecialidades(String.join(", ", data.especialidades()));
        }

        if (data.endereco() != null) {
            Endereco endereco = new Endereco();
            endereco.setCep(data.endereco().cep());
            endereco.setLogradouro(data.endereco().logradouro());
            endereco.setNumero(data.endereco().numero());
            endereco.setComplemento(data.endereco().complemento());
            endereco.setBairro(data.endereco().bairro());
            endereco.setCidade(data.endereco().cidade());
            endereco.setEstado(data.endereco().estado());
            empresa.setEndereco(endereco);
        }

        usuarioRepository.save(empresa);

        if (data.servicos() != null) {
            for (ServicoRegistroRequest servicoData : data.servicos()) {
                Servico servico = new Servico();
                servico.setNome(servicoData.nome());
                servico.setPreco(parsePreco(servicoData.preco()));
                servico.setDuracaoMinutos(parseDuracaoMinutos(servicoData.duracao()));
                servico.setEmpresa(empresa);
                servicoRepository.save(servico);
            }
        }
    }

    private BigDecimal parsePreco(String bruto) {
        if (bruto == null || bruto.isBlank()) return BigDecimal.ZERO;
        String normalizado = bruto.replaceAll("[^0-9,.]", "").replace(",", ".");
        try {
            return new BigDecimal(normalizado);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer parseDuracaoMinutos(String bruto) {
        if (bruto == null) return 0;
        Matcher matcher = Pattern.compile("\\d+").matcher(bruto);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }
}