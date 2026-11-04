package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.FotoPortfolio;
import br.com.kira.kirabackend.domain.entity.Usuario;
import br.com.kira.kirabackend.dto.response.FotoPortfolioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.FotoPortfolioRepository;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    static final int MAXIMO_DE_FOTOS = 20;

    private final FotoPortfolioRepository fotoPortfolioRepository;
    private final UsuarioRepository usuarioRepository;
    private final ImagensEnviadas imagens;

    public List<FotoPortfolioResponse> listar(UUID empresaId) {
        buscarEmpresaAtiva(empresaId);
        return fotoPortfolioRepository.findByEmpresaIdOrderByDataCriacaoDesc(empresaId).stream()
                .map(PortfolioService::toResponse)
                .toList();
    }

    public FotoPortfolioResponse adicionar(UUID empresaId, MultipartFile arquivo) {
        Empresa empresa = buscarEmpresaAtiva(empresaId);
        if (fotoPortfolioRepository.countByEmpresaId(empresaId) >= MAXIMO_DE_FOTOS) {
            throw new RegraDeNegocioException("O portfólio pode ter no máximo " + MAXIMO_DE_FOTOS + " fotos");
        }
        String url = imagens.guardar(arquivo);
        FotoPortfolio foto = new FotoPortfolio();
        foto.setEmpresa(empresa);
        foto.setUrl(url);
        try {
            return toResponse(fotoPortfolioRepository.save(foto));
        } catch (RuntimeException e) {
            imagens.apagarSemFalhar(url);
            throw e;
        }
    }

    public void remover(UUID empresaId, UUID fotoId) {
        // Procura pelo estúdio também: a foto de outro estúdio responde como inexistente
        FotoPortfolio foto = fotoPortfolioRepository.findByIdAndEmpresaId(fotoId, empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Foto não encontrada"));
        fotoPortfolioRepository.delete(foto);
        imagens.apagarSemFalhar(foto.getUrl());
    }

    // Empresa desativada responde como inexistente, igual às outras rotas da vitrine
    private Empresa buscarEmpresaAtiva(UUID empresaId) {
        Usuario usuario = usuarioRepository.findById(empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estúdio não encontrado"));
        if (!(usuario instanceof Empresa empresa) || !empresa.isEnabled()) {
            throw new RecursoNaoEncontradoException("Estúdio não encontrado");
        }
        return empresa;
    }

    private static FotoPortfolioResponse toResponse(FotoPortfolio foto) {
        return new FotoPortfolioResponse(foto.getId(), foto.getUrl(), foto.getDataCriacao());
    }
}
