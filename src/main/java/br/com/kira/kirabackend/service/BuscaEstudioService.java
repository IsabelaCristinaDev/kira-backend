package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.request.BuscaEstudioFiltro;
import br.com.kira.kirabackend.dto.response.EnderecoResponse;
import br.com.kira.kirabackend.dto.response.EstudioBuscaResponse;
import br.com.kira.kirabackend.dto.response.PaginaResponse;
import br.com.kira.kirabackend.dto.response.ServicoResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository.Coordenadas;
import br.com.kira.kirabackend.repository.BuscaEstudioRepository.Linha;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BuscaEstudioService {

    static final int TAMANHO_MAXIMO = 50;
    private static final int MAXIMO_NO_TEXTO = 100;

    private final BuscaEstudioRepository buscaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ServicoRepository servicoRepository;

    /** A distância usa as coordenadas do perfil de quem está logado: a localização não precisa ir no endereço da chamada. */
    @Transactional(readOnly = true)
    public PaginaResponse<EstudioBuscaResponse> buscar(BuscaEstudioFiltro filtro, Usuario usuario, int pagina, int tamanho) {
        validar(filtro, pagina, tamanho);
        Coordenadas origem = coordenadasDe(usuario);

        long total = buscaRepository.contar(filtro, origem);
        List<Linha> linhas = total == 0 ? List.of() : buscaRepository.buscar(filtro, origem, pagina, tamanho);
        int paginas = (int) Math.ceil((double) total / tamanho);
        return new PaginaResponse<>(montar(linhas), pagina, tamanho, total, paginas);
    }

    private List<EstudioBuscaResponse> montar(List<Linha> linhas) {
        if (linhas.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = linhas.stream().map(Linha::id).toList();
        Map<UUID, Usuario> empresas = usuarioRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<UUID, List<ServicoResponse>> servicos = servicoRepository.findByEmpresaIdInAndAtivoTrue(ids).stream()
                .sorted(Comparator.comparing(Servico::getPreco))
                .collect(Collectors.groupingBy(s -> s.getEmpresa().getId(),
                        Collectors.mapping(ServicoResponse::from, Collectors.toList())));
        return linhas.stream()
                .map(linha -> paraResposta(linha, (Empresa) empresas.get(linha.id()),
                        servicos.getOrDefault(linha.id(), List.of())))
                .toList();
    }

    private static EstudioBuscaResponse paraResposta(Linha linha, Empresa empresa, List<ServicoResponse> servicos) {
        return new EstudioBuscaResponse(
                empresa.getId(),
                empresa.getNome(),
                empresa.getDescricao(),
                EnderecoResponse.from(empresa.getEndereco()),
                empresa.getTelefone(),
                empresa.getFotoUrl(),
                empresa.getTipoEstabelecimento(),
                linha.media(),
                linha.total(),
                linha.menorPreco(),
                empresa.getLatitude(),
                empresa.getLongitude(),
                linha.distanciaKm(),
                servicos);
    }

    private static Coordenadas coordenadasDe(Usuario usuario) {
        if (usuario instanceof Cliente cliente && cliente.getLatitude() != null && cliente.getLongitude() != null) {
            return new Coordenadas(cliente.getLatitude(), cliente.getLongitude());
        }
        return null;
    }

    private static void validar(BuscaEstudioFiltro filtro, int pagina, int tamanho) {
        if (pagina < 0) {
            throw new RegraDeNegocioException("A página não pode ser negativa");
        }
        if (tamanho < 1 || tamanho > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException("O tamanho da página deve ser de 1 a " + TAMANHO_MAXIMO);
        }
        if (longo(filtro.texto()) || longo(filtro.cidade()) || longo(filtro.servico())) {
            throw new RegraDeNegocioException("O texto da busca deve ter no máximo " + MAXIMO_NO_TEXTO + " caracteres");
        }
        if (filtro.precoMaximo() != null && filtro.precoMaximo().compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraDeNegocioException("O preço máximo não pode ser negativo");
        }
        if (filtro.notaMinima() != null && (filtro.notaMinima() < 0 || filtro.notaMinima() > 5)) {
            throw new RegraDeNegocioException("A nota mínima deve ser de 0 a 5");
        }
        if (filtro.distanciaMaximaKm() != null && filtro.distanciaMaximaKm() < 0) {
            throw new RegraDeNegocioException("A distância máxima não pode ser negativa");
        }
    }

    private static boolean longo(String valor) {
        return valor != null && valor.length() > MAXIMO_NO_TEXTO;
    }
}
