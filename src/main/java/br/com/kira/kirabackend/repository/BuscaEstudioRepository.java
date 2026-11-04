package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.enums.OrdenacaoBusca;
import br.com.kira.kirabackend.dto.request.BuscaEstudioFiltro;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A busca monta o SQL pelos filtros preenchidos. Os valores vão sempre como parâmetros;
 * só entram no texto do SQL trechos fixos daqui.
 */
@RequiredArgsConstructor
@Repository
public class BuscaEstudioRepository {

    public record Coordenadas(double latitude, double longitude) {}

    public record Linha(UUID id, BigDecimal menorPreco, Double media, long total, Double distanciaKm) {}

    private static final String COM_ACENTO = "áàâãäéèêëíìîïóòôõöúùûüç";
    private static final String SEM_ACENTO = "aaaaaeeeeiiiiooooouuuuc";

    private static final String DISTANCIA = """
            CASE WHEN e.latitude IS NULL OR e.longitude IS NULL THEN NULL ELSE
              6371 * acos(least(1, greatest(-1,
                cos(radians(:lat)) * cos(radians(e.latitude)) * cos(radians(e.longitude) - radians(:lng))
                + sin(radians(:lat)) * sin(radians(e.latitude))
              )))
            END""";

    private final NamedParameterJdbcTemplate jdbc;

    public long contar(BuscaEstudioFiltro filtro, Coordenadas origem) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String sql = "SELECT count(*) FROM (" + base(filtro, origem, parametros) + ") b" + filtroCalculado(filtro, origem, parametros);
        Long total = jdbc.queryForObject(sql, parametros, Long.class);
        return total == null ? 0 : total;
    }

    public List<Linha> buscar(BuscaEstudioFiltro filtro, Coordenadas origem, int pagina, int tamanho) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String sql = "SELECT * FROM (" + base(filtro, origem, parametros) + ") b"
                + filtroCalculado(filtro, origem, parametros)
                + " ORDER BY " + ordem(filtro.ordenacao())
                + " LIMIT :limite OFFSET :deslocamento";
        parametros.addValue("limite", tamanho);
        parametros.addValue("deslocamento", (long) pagina * tamanho);
        return jdbc.query(sql, parametros, (rs, numero) -> new Linha(
                rs.getObject("id", UUID.class),
                rs.getBigDecimal("menor_preco"),
                rs.getObject("media", Double.class),
                rs.getLong("total"),
                rs.getObject("distancia", Double.class)));
    }

    private String base(BuscaEstudioFiltro filtro, Coordenadas origem, MapSqlParameterSource parametros) {
        List<String> condicoes = new ArrayList<>(List.of("u.ativo = true"));

        if (preenchido(filtro.texto())) {
            parametros.addValue("texto", padraoContem(filtro.texto()));
            condicoes.add("(" + contem("u.nome", "texto")
                    + " OR " + contem("e.descricao", "texto")
                    + " OR " + contem("e.especialidades", "texto")
                    + " OR EXISTS (SELECT 1 FROM servico st WHERE st.empresa_id = e.id AND st.ativo = true AND "
                    + contem("st.nome", "texto") + "))");
        }
        if (preenchido(filtro.cidade())) {
            parametros.addValue("cidade", padraoContem(filtro.cidade()));
            condicoes.add(contem("e.endereco_cidade", "cidade"));
        }
        if (filtro.tipo() != null) {
            parametros.addValue("tipo", filtro.tipo().name());
            condicoes.add("e.tipo_estabelecimento = :tipo");
        }
        adicionarFiltroDeServico(filtro, parametros, condicoes);

        String distancia = "NULL::double precision";
        if (origem != null) {
            parametros.addValue("lat", origem.latitude());
            parametros.addValue("lng", origem.longitude());
            distancia = DISTANCIA;
        }
        return """
                SELECT e.id,
                  (SELECT min(s.preco) FROM servico s WHERE s.empresa_id = e.id AND s.ativo = true) AS menor_preco,
                  (SELECT avg(a.nota)::double precision FROM avaliacao a
                    WHERE a.empresa_id = e.id AND a.tipo_avaliador = 'CLIENTE') AS media,
                  (SELECT count(*) FROM avaliacao a
                    WHERE a.empresa_id = e.id AND a.tipo_avaliador = 'CLIENTE') AS total,
                  %s AS distancia
                FROM empresa e JOIN usuario u ON u.id = e.id
                WHERE %s""".formatted(distancia, String.join(" AND ", condicoes));
    }

    // Com serviço ou categoria, o preço vale para esse serviço: um corte barato não coloca o estúdio numa busca de unhas
    private void adicionarFiltroDeServico(BuscaEstudioFiltro filtro, MapSqlParameterSource parametros, List<String> condicoes) {
        List<String> doServico = new ArrayList<>();
        if (preenchido(filtro.servico())) {
            parametros.addValue("servico", padraoContem(filtro.servico()));
            doServico.add(contem("sf.nome", "servico"));
        }
        if (filtro.categoria() != null) {
            parametros.addValue("categoria", filtro.categoria().name());
            doServico.add("sf.categoria = :categoria");
        }
        if (doServico.isEmpty()) {
            return;
        }
        if (filtro.precoMaximo() != null) {
            parametros.addValue("precoMaximo", filtro.precoMaximo());
            doServico.add("sf.preco <= :precoMaximo");
        }
        condicoes.add("EXISTS (SELECT 1 FROM servico sf WHERE sf.empresa_id = e.id AND sf.ativo = true AND "
                + String.join(" AND ", doServico) + ")");
    }

    // Filtros sobre os valores calculados. Estúdio sem preço ou sem coordenadas não some da lista por isso.
    private String filtroCalculado(BuscaEstudioFiltro filtro, Coordenadas origem, MapSqlParameterSource parametros) {
        List<String> condicoes = new ArrayList<>();
        boolean filtroDeServico = preenchido(filtro.servico()) || filtro.categoria() != null;
        if (filtro.precoMaximo() != null && !filtroDeServico) {
            parametros.addValue("precoMaximo", filtro.precoMaximo());
            condicoes.add("(b.menor_preco IS NULL OR b.menor_preco <= :precoMaximo)");
        }
        if (filtro.notaMinima() != null && filtro.notaMinima() > 0) {
            parametros.addValue("notaMinima", filtro.notaMinima());
            condicoes.add("b.media >= :notaMinima");
        }
        if (filtro.distanciaMaximaKm() != null && origem != null) {
            parametros.addValue("distanciaMaxima", filtro.distanciaMaximaKm());
            condicoes.add("(b.distancia IS NULL OR b.distancia <= :distanciaMaxima)");
        }
        return condicoes.isEmpty() ? "" : " WHERE " + String.join(" AND ", condicoes);
    }

    // O id no fim mantém a ordem estável entre as páginas
    private static String ordem(OrdenacaoBusca ordenacao) {
        return switch (ordenacao == null ? OrdenacaoBusca.PROXIMO : ordenacao) {
            case PROXIMO -> "b.distancia ASC NULLS LAST, b.media DESC NULLS LAST, b.id";
            case AVALIADO -> "b.media DESC NULLS LAST, b.total DESC, b.id";
            case PRECO -> "b.menor_preco ASC NULLS LAST, b.media DESC NULLS LAST, b.id";
            case RECOMENDADO -> "b.total DESC, b.media DESC NULLS LAST, b.id";
        };
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    private static String contem(String coluna, String parametro) {
        return "translate(lower(" + coluna + "), '" + COM_ACENTO + "', '" + SEM_ACENTO + "') LIKE :" + parametro + " ESCAPE '\\'";
    }

    // Sem acento e sem maiúscula, e com os curingas do LIKE valendo como texto comum
    static String padraoContem(String valor) {
        String texto = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + texto + "%";
    }
}
