package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.BloqueioHorario;
import br.com.kira.kirabackend.domain.entity.Funcionaria;
import br.com.kira.kirabackend.dto.request.BloqueioHorarioRequest;
import br.com.kira.kirabackend.dto.response.BloqueioHorarioResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.BloqueioHorarioRepository;
import br.com.kira.kirabackend.repository.FuncionariaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BloqueioHorarioService {

    @Autowired
    private BloqueioHorarioRepository bloqueioHorarioRepository;

    @Autowired
    private FuncionariaRepository funcionariaRepository;

    public BloqueioHorarioResponse cadastrar(UUID funcionariaId,
                                             BloqueioHorarioRequest request) {
        Funcionaria funcionaria = funcionariaRepository.findById(funcionariaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcionária não encontrada"));

        if (request.dataHoraFim().isBefore(request.dataHoraInicio())) {
            throw new RegraDeNegocioException("Data de fim deve ser após data de início");
        }

        BloqueioHorario bloqueio = new BloqueioHorario();
        bloqueio.setFuncionaria(funcionaria);
        bloqueio.setDataHoraInicio(request.dataHoraInicio());
        bloqueio.setDataHoraFim(request.dataHoraFim());
        bloqueio.setMotivo(request.motivo());

        BloqueioHorario salvo = bloqueioHorarioRepository.save(bloqueio);
        return toResponse(salvo);
    }

    public List<BloqueioHorarioResponse> listarPorFuncionaria(UUID funcionariaId) {
        return bloqueioHorarioRepository.findByFuncionariaId(funcionariaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void remover(UUID id) {
        bloqueioHorarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio não encontrado"));
        bloqueioHorarioRepository.deleteById(id);
    }

    private BloqueioHorarioResponse toResponse(BloqueioHorario b) {
        return new BloqueioHorarioResponse(
                b.getId(),
                b.getFuncionaria().getId(),
                b.getFuncionaria().getNome(),
                b.getDataHoraInicio(),
                b.getDataHoraFim(),
                b.getMotivo()
        );
    }
}