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
import br.com.kira.kirabackend.exception.DocumentoJaCadastradoException;
import br.com.kira.kirabackend.exception.EmailJaCadastradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.RefreshTokenRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import br.com.kira.kirabackend.security.JwtTokenProvider;
import br.com.kira.kirabackend.util.KiraTimeZone;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
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

        var usuario = usuarioRepository.findByEmailIgnoreCase(data.email())
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
        String email = normalizarEmail(data.email());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailJaCadastradoException();
        }
        if (usuarioRepository.existsClienteByCpf(data.cpf())) {
            throw new DocumentoJaCadastradoException("CPF já cadastrado");
        }

        Cliente cliente = new Cliente();
        cliente.setNome(data.nome());
        cliente.setEmail(email);
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
        String email = normalizarEmail(data.email());
        // CNPJ e CPF são opcionais; texto vazio vira null para não colidir na restrição unique do banco
        String cnpj = vazioParaNulo(data.cnpj());
        String cpf = vazioParaNulo(data.cpf());

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailJaCadastradoException();
        }
        if (cnpj != null && usuarioRepository.existsEmpresaByCnpj(cnpj)) {
            throw new DocumentoJaCadastradoException("CNPJ já cadastrado");
        }
        if (cpf != null && usuarioRepository.existsEmpresaByCpf(cpf)) {
            throw new DocumentoJaCadastradoException("CPF já cadastrado");
        }

        Empresa empresa = new Empresa();
        empresa.setNome(data.nome());
        empresa.setEmail(email);
        empresa.setSenhaHash(passwordEncoder.encode(data.senha()));
        empresa.setTelefone(data.telefone());
        empresa.setCnpj(cnpj);
        empresa.setCpf(cpf);
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

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /**
     * Converte o preço digitado no cadastro ("R$ 50", "45,00", "1.234,56", "50.00").
     * Com vírgula, segue o formato brasileiro: ponto separa milhar e vírgula separa centavos.
     * Sem vírgula, um único ponto é decimal e vários pontos são separadores de milhar.
     */
    private BigDecimal parsePreco(String bruto) {
        if (bruto == null || bruto.isBlank()) return BigDecimal.ZERO;
        String numeros = bruto.replaceAll("[^0-9,.]", "");

        String normalizado;
        if (numeros.contains(",")) {
            normalizado = numeros.replace(".", "").replace(",", ".");
        } else if (numeros.indexOf('.') != numeros.lastIndexOf('.')) {
            normalizado = numeros.replace(".", "");
        } else {
            normalizado = numeros;
        }

        try {
            return new BigDecimal(normalizado).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static final Pattern DURACAO_COM_HORAS =
            Pattern.compile("(?<!\\d)(\\d++)\\s*+h(?:oras?)?\\s*+(?:(\\d++)\\s*+(?:min(?:utos?)?)?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERO = Pattern.compile("\\d+");

    /**
     * Converte a duração digitada no cadastro para minutos:
     * "45 min" = 45, "1h" = 60, "1h30" = 90, "2 horas" = 120.
     */
    private Integer parseDuracaoMinutos(String bruto) {
        if (bruto == null) return 0;

        try {
            Matcher horas = DURACAO_COM_HORAS.matcher(bruto);
            if (horas.find()) {
                int minutos = horas.group(2) != null ? Integer.parseInt(horas.group(2)) : 0;
                return Math.addExact(Math.multiplyExact(Integer.parseInt(horas.group(1)), 60), minutos);
            }

            Matcher numero = NUMERO.matcher(bruto);
            return numero.find() ? Integer.parseInt(numero.group()) : 0;
        } catch (NumberFormatException | ArithmeticException e) {
            return 0;
        }
    }
}