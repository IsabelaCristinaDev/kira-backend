package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.dto.response.CompatibilidadeEstudioResponse;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FotoPortfolioRepository;
import br.com.kira.kirabackend.repository.ServicoRepository;
import br.com.kira.kirabackend.service.ComparadorDePortfolio.Foto;
import br.com.kira.kirabackend.service.ComparadorDePortfolio.Semelhanca;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Ordena os estúdios de cabelo pela semelhança do portfólio com a foto de referência do Provador.
 *
 * O portfólio inteiro do estúdio é comparado, em grupos de até 3 fotos por chamada (o máximo que o
 * kira-ai aceita), e a nota dele é a da foto mais parecida. Fotos que a IA não reconhece como cabelo não contam; estúdio sem nenhuma foto
 * de cabelo fica fora da lista, e o app o mostra depois, na ordem que já calcula.
 *
 * A foto de referência só passa pela memória: não é gravada em disco nem no banco.
 */
@Service
@RequiredArgsConstructor
public class CompatibilidadePortfolioService {

    private final ServicoRepository servicoRepository;
    private final FotoPortfolioRepository fotoPortfolioRepository;
    private final ImagensEnviadas imagens;
    private final ComparadorDePortfolio comparador;

    // Sem @Transactional de propósito: as chamadas à IA demoram e não devem segurar uma conexão do banco
    public List<CompatibilidadeEstudioResponse> ranquear(MultipartFile referencia) {
        if (referencia == null) {
            throw new RegraDeNegocioException("Envie a foto de referência");
        }
        byte[] conteudoReferencia = imagens.lerImagem(referencia);

        List<CompatibilidadeEstudioResponse> ranking = new ArrayList<>();
        for (UUID estudioId : estudiosDeCabelo()) {
            List<Foto> fotos = fotosDoPortfolio(estudioId);
            if (fotos.isEmpty()) {
                continue;
            }
            melhorSemelhanca(conteudoReferencia, fotos)
                    .ifPresent(melhor -> ranking.add(new CompatibilidadeEstudioResponse(estudioId, melhor)));
        }

        ranking.sort(Comparator.comparingDouble(CompatibilidadeEstudioResponse::similaridade).reversed());
        return ranking;
    }

    // Antes só iam as 3 fotos mais recentes, e a foto parecida com a referência podia nunca ser comparada
    private OptionalDouble melhorSemelhanca(byte[] referencia, List<Foto> fotos) {
        OptionalDouble melhor = OptionalDouble.empty();
        for (int inicio = 0; inicio < fotos.size(); inicio += ComparadorDePortfolio.MAXIMO_DE_FOTOS) {
            List<Foto> grupo = fotos.subList(inicio, Math.min(inicio + ComparadorDePortfolio.MAXIMO_DE_FOTOS, fotos.size()));
            OptionalDouble doGrupo = comparador.comparar(referencia, grupo).stream()
                    .mapToDouble(Semelhanca::percentual)
                    .max();
            if (doGrupo.isPresent() && (melhor.isEmpty() || doGrupo.getAsDouble() > melhor.getAsDouble())) {
                melhor = doGrupo;
            }
        }
        return melhor;
    }

    // Serviço antigo sem categoria é reconhecido pelo nome, igual ao resto da API
    private Set<UUID> estudiosDeCabelo() {
        Set<UUID> estudios = new LinkedHashSet<>();
        for (Servico servico : servicoRepository.findByAtivoTrueAndEmpresaAtivoTrue()) {
            CategoriaServico categoria = CategoriaServico.escolhidaOuPeloNome(servico.getCategoria(), servico.getNome());
            if (categoria == CategoriaServico.CABELO) {
                estudios.add(servico.getEmpresa().getId());
            }
        }
        return estudios;
    }

    // O nome do arquivo identifica a foto na resposta do kira-ai. Foto cujo arquivo sumiu da pasta é pulada
    private List<Foto> fotosDoPortfolio(UUID estudioId) {
        List<Foto> fotos = new ArrayList<>();
        for (FotoPortfolio foto : fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(estudioId)) {
            Optional<byte[]> conteudo = imagens.lerGuardada(foto.getUrl());
            conteudo.ifPresent(bytes -> fotos.add(new Foto(nomeDoArquivo(foto.getUrl()), bytes)));
        }
        return fotos;
    }

    private static String nomeDoArquivo(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
