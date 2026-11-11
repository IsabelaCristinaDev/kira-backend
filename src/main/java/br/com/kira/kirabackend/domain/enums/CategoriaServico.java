package br.com.kira.kirabackend.domain.enums;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

public enum CategoriaServico {
    // A ordem decide o empate: "alongamento de cílios" é CILIOS, não UNHAS; "corte de barba" é BARBA
    CILIOS("cilio", "lash"),
    SOBRANCELHAS("sobrancelha", "brow"),
    MAQUIAGEM("maquiag", "make"),
    BARBA("barba", "barbear", "navalha"),
    UNHAS("unha", "manicure", "pedicure", "esmalta", "nail", "alongamento"),
    MASSAGEM("massag", "drenagem"),
    ESTETICA("estetic", "limpeza de pele", "peeling", "facial"),
    CABELO("cabel", "corte", "escova", "color", "hidrata", "penteado", "progressiva", "mecha", "luzes"),
    OUTRO;

    private final List<String> palavras;

    CategoriaServico(String... palavras) {
        this.palavras = List.of(palavras);
    }

    /**
     * Categoria escolhida ou, sem escolha, a deduzida pelo nome do serviço.
     * Cobre os serviços antigos (sem categoria no banco) e o app que ainda não manda o campo.
     */
    public static CategoriaServico escolhidaOuPeloNome(CategoriaServico escolhida, String nome) {
        return escolhida != null ? escolhida : peloNome(nome);
    }

    public static CategoriaServico peloNome(String nome) {
        if (nome == null) {
            return OUTRO;
        }
        String texto = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        for (CategoriaServico categoria : values()) {
            if (categoria.palavras.stream().anyMatch(texto::contains)) {
                return categoria;
            }
        }
        return OUTRO;
    }
}
