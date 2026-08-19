package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.domain.entity.RefreshToken;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.ClienteRegistroRequest;
import br.com.kira.kirabackend.dto.request.EmpresaRegistroRequest;
import br.com.kira.kirabackend.dto.request.LoginRequest;
import br.com.kira.kirabackend.dto.request.RefreshTokenRequest;
import br.com.kira.kirabackend.dto.request.ServicoRegistroRequest;
import br.com.kira.kirabackend.dto.response.LoginResponse;
import br.com.kira.kirabackend.exception.EmailJaCadastradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final ServicoRepository servicoRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;


    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Transactional
    public LoginResponse login(LoginRequest data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(
                data.email(), data.senha());
        var auth = authenticationManager.authenticate(usernamePassword);

        var usuario = usuarioRepository.findByEmail(data.email())
                .orElseThrow();

        String tipoUsuario = usuario.getClass().getSimpleName().toUpperCase();
        var token = jwtTokenProvider.gerarToken(
                (UserDetails) auth.getPrincipal(), tipoUsuario);
        var refreshToken = gerarERegistrarRefreshToken(usuario);

        return new LoginResponse(token, refreshToken, tipoUsuario, usuario.getNome(), usuario.getEmail());
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest data) {
        var refreshTokenEntity = refreshTokenRepository.findByToken(data.refreshToken())
                .orElseThrow(() -> new RegraDeNegocioException("Refresh token inválido"));

        if (!refreshTokenEntity.isValido()) {
            throw new RegraDeNegocioException("Refresh token inválido ou expirado");
        }

        Usuario usuario = refreshTokenEntity.getUsuario();
        String tipoUsuario = Hibernate.getClass(usuario).getSimpleName().toUpperCase();
        var novoToken = jwtTokenProvider.gerarToken(usuario, tipoUsuario);
        var novoRefreshToken = gerarERegistrarRefreshToken(usuario);

        return new LoginResponse(novoToken, novoRefreshToken, tipoUsuario, usuario.getNome(), usuario.getEmail());
    }

    @Transactional
    public void logout(RefreshTokenRequest data) {
        refreshTokenRepository.findByToken(data.refreshToken())
                .ifPresent(refreshTokenEntity -> {
                    refreshTokenEntity.setRevogado(true);
                    refreshTokenRepository.save(refreshTokenEntity);
                });
    }

    private String gerarERegistrarRefreshToken(Usuario usuario) {
        refreshTokenRepository.deleteByUsuarioId(usuario.getId());

        String token = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUsuario(usuario);
        refreshToken.setDataExpiracao(LocalDateTime.now(KiraTimeZone.DEFAULT).plusSeconds(refreshExpiration / 1000));
        refreshToken.setRevogado(false);

        refreshTokenRepository.save(refreshToken);

        return token;
    }

    public void registrarCliente(ClienteRegistroRequest data) {
        if (usuarioRepository.existsByEmail(data.email())) {
            throw new EmailJaCadastradoException();
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
        emailService.enviarEmailBoasVindas(cliente.getEmail(), cliente.getNome());

    }

    @Transactional
    public void registrarEmpresa(EmpresaRegistroRequest data) {
        if (usuarioRepository.existsByEmail(data.email())) {
            throw new EmailJaCadastradoException();
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
        emailService.enviarEmailBoasVindas(empresa.getEmail(), empresa.getNome());

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