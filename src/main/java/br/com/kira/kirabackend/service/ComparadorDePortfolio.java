package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.exception.CompatibilidadeIndisponivelException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Quem compara a foto de referência com as fotos de portfólio. Hoje é o kira-ai (serviço Python da Wanessa);
 * trocar o serviço não mexe no resto.
 */
public interface ComparadorDePortfolio {

    /** O kira-ai recebe no máximo esta quantidade de fotos de portfólio por chamada. */
    int MAXIMO_DE_FOTOS = 3;

    /**
     * Compara a referência com até {@link #MAXIMO_DE_FOTOS} fotos, todas como procedimento de cabelo.
     *
     * @return a semelhança só das fotos reconhecidas como cabelo; as de outra categoria ficam de fora
     * @throws CompatibilidadeIndisponivelException se o serviço não responder ou fugir do contrato
     */
    List<Semelhanca> comparar(byte[] referencia, List<Foto> fotos);

    /** Foto de portfólio. O nome volta na resposta e é por ele que se sabe qual foto é qual. */
    record Foto(String nome, byte[] conteudo) {

        // O record compara arrays pela referência; aqui a igualdade é pelo conteúdo da foto
        @Override
        public boolean equals(Object outro) {
            return outro instanceof Foto foto && Objects.equals(nome, foto.nome) && Arrays.equals(conteudo, foto.conteudo);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(nome) + Arrays.hashCode(conteudo);
        }

        // Só o nome e o tamanho: os bytes da foto nunca devem ir parar num log
        @Override
        public String toString() {
            return "Foto[" + nome + ", " + (conteudo == null ? 0 : conteudo.length) + " bytes]";
        }
    }

    /** Semelhança de uma foto com a referência, de 0 a 100. */
    record Semelhanca(String nome, double percentual) {}
}
