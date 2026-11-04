package br.com.kira.kirabackend.repository;

import br.com.kira.kirabackend.domain.enums.OrdenacaoBusca;
import br.com.kira.kirabackend.dto.request.BuscaEstudioFiltro;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Consulta fixa: filtro vazio vai nulo e não restringe nada, e os valores vão sempre como parâmetros. */
@RequiredArgsConstructor
@Repository
public class BuscaEstudioRepository {

    public record Coordenadas(double latitude, double longitude) {}

    public record Linha(UUID id, BigDecimal menorPreco, Double media, long total, Double distanciaKm) {}

    private static final String SEM_ACENTO_INICIO = "translate(lower(";
    private static final String SEM_ACENTO_FIM = "), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc')";
    private static final String CONTEM_TEXTO = SEM_ACENTO_FIM + " LIKE :texto ESCAPE '\\'";

    private static final String BASE = """
            SELECT e.id,
              (SELECT min(s.preco) FROM servico s WHERE s.empresa_id = e.id AND s.ativo = true) AS menor_preco,
              (SELECT avg(a.nota)::double precision FROM avaliacao a
                WHERE a.empresa_id = e.id AND a.tipo_avaliador = 'CLIENTE') AS media,
              (SELECT count(*) FROM avaliacao a
                WHERE a.empresa_id = e.id AND a.tipo_avaliador = 'CLIENTE') AS total,
              CASE WHEN CAST(:lat AS double precision) IS NULL OR e.latitude IS NULL OR e.longitude IS NULL THEN NULL ELSE
                6371 * acos(least(1, greatest(-1,
                  cos(radians(CAST(:lat AS double precision))) * cos(radians(e.latitude))
                    * cos(radians(e.longitude) - radians(CAST(:lng AS double precision)))
                  + sin(radians(CAST(:lat AS double precision))) * sin(radians(e.latitude))
                )))
              END AS distancia
            FROM empresa e JOIN usuario u ON u.id = e.id
            WHERE u.ativo = true
              AND (CAST(:texto AS text) IS NULL
            """
            + " OR " + SEM_ACENTO_INICIO + "u.nome" + CONTEM_TEXTO
            + " OR " + SEM_ACENTO_INICIO + "e.descricao" + CONTEM_TEXTO
            + " OR " + SEM_ACENTO_INICIO + "e.especialidades" + CONTEM_TEXTO
            + " OR EXISTS (SELECT 1 FROM servico st WHERE st.empresa_id = e.id AND st.ativo = true AND "
            + SEM_ACENTO_INICIO + "st.nome" + CONTEM_TEXTO + "))"
            + " AND (CAST(:cidade AS text) IS NULL OR "
            + SEM_ACENTO_INICIO + "e.endereco_cidade" + SEM_ACENTO_FIM + " LIKE :cidade ESCAPE '\\')"
            + """

              AND (CAST(:tipo AS text) IS NULL OR e.tipo_estabelecimento = :tipo)
              AND ((CAST(:servico AS text) IS NULL AND CAST(:categoria AS text) IS NULL)
                OR EXISTS (SELECT 1 FROM servico sf WHERE sf.empresa_id = e.id AND sf.ativo = true
                  AND (CAST(:categoria AS text) IS NULL OR sf.categoria = :categoria)
                  AND (CAST(:precoMaximo AS numeric) IS NULL OR sf.preco <= :precoMaximo)
                  AND (CAST(:servico AS text) IS NULL OR
            """
            + SEM_ACENTO_INICIO + "sf.nome" + SEM_ACENTO_FIM + " LIKE :servico ESCAPE '\\')))";

    // Estúdio sem preço ou sem coordenadas não some da lista por isso. Com serviço ou categoria, o preço
    // já foi conferido no serviço: um corte barato não coloca o estúdio numa busca de unhas.
    private static final String FILTROS_CALCULADOS = """
            WHERE (CAST(:precoMaximo AS numeric) IS NULL
                OR CAST(:servico AS text) IS NOT NULL OR CAST(:categoria AS text) IS NOT NULL
                OR b.menor_preco IS NULL OR b.menor_preco <= :precoMaximo)
              AND (CAST(:notaMinima AS double precision) IS NULL OR b.media >= :notaMinima)
              AND (CAST(:distanciaMaxima AS double precision) IS NULL
                OR b.distancia IS NULL OR b.distancia <= :distanciaMaxima)
            """;

    private static final String CONTAR = "SELECT count(*) FROM (" + BASE + ") b " + FILTROS_CALCULADOS;

    // O id no fim mantém a ordem estável entre as páginas
    private static final String BUSCAR = "SELECT * FROM (" + BASE + ") b " + FILTROS_CALCULADOS + """
            ORDER BY
              CASE WHEN :ordem = 'PROXIMO' THEN b.distancia END ASC NULLS LAST,
              CASE WHEN :ordem = 'PRECO' THEN b.menor_preco END ASC NULLS LAST,
              CASE WHEN :ordem = 'RECOMENDADO' THEN b.total END DESC NULLS LAST,
              b.media DESC NULLS LAST,
              b.total DESC,
              b.id
            LIMIT :limite OFFSET :deslocamento
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public long contar(BuscaEstudioFiltro filtro, Coordenadas origem) {
        Long total = jdbc.queryForObject(CONTAR, parametros(filtro, origem), Long.class);
        return total == null ? 0 : total;
    }

    public List<Linha> buscar(BuscaEstudioFiltro filtro, Coordenadas origem, int pagina, int tamanho) {
        OrdenacaoBusca ordenacao = filtro.ordenacao() == null ? OrdenacaoBusca.PROXIMO : filtro.ordenacao();
        MapSqlParameterSource parametros = parametros(filtro, origem)
                .addValue("ordem", ordenacao.name(), Types.VARCHAR)
                .addValue("limite", tamanho)
                .addValue("deslocamento", (long) pagina * tamanho);
        return jdbc.query(BUSCAR, parametros, (rs, numero) -> new Linha(
                rs.getObject("id", UUID.class),
                rs.getBigDecimal("menor_preco"),
                rs.getObject("media", Double.class),
                rs.getLong("total"),
                rs.getObject("distancia", Double.class)));
    }

    private static MapSqlParameterSource parametros(BuscaEstudioFiltro filtro, Coordenadas origem) {
        // Sem login de cliente não há de onde medir, e nota zero não restringe nada
        Double notaMinima = filtro.notaMinima() != null && filtro.notaMinima() > 0 ? filtro.notaMinima() : null;
        return new MapSqlParameterSource()
                .addValue("texto", padraoContem(filtro.texto()), Types.VARCHAR)
                .addValue("cidade", padraoContem(filtro.cidade()), Types.VARCHAR)
                .addValue("servico", padraoContem(filtro.servico()), Types.VARCHAR)
                .addValue("tipo", filtro.tipo() == null ? null : filtro.tipo().name(), Types.VARCHAR)
                .addValue("categoria", filtro.categoria() == null ? null : filtro.categoria().name(), Types.VARCHAR)
                .addValue("precoMaximo", filtro.precoMaximo(), Types.NUMERIC)
                .addValue("notaMinima", notaMinima, Types.DOUBLE)
                .addValue("distanciaMaxima", origem == null ? null : filtro.distanciaMaximaKm(), Types.DOUBLE)
                .addValue("lat", origem == null ? null : origem.latitude(), Types.DOUBLE)
                .addValue("lng", origem == null ? null : origem.longitude(), Types.DOUBLE);
    }

    // Sem acento e sem maiúscula, e com os curingas do LIKE valendo como texto comum. Em branco vira filtro vazio.
    private static String padraoContem(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + texto + "%";
    }
}
