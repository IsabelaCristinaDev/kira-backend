package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.domain.entity.Servico;
import br.com.kira.kirabackend.domain.enums.CategoriaServico;
import br.com.kira.kirabackend.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Serviços antigos não têm categoria no banco, e a busca filtra por essa coluna. Os novos já são salvos com ela. */
@RequiredArgsConstructor
@Component
public class PreenchimentoCategoriaServicos implements ApplicationRunner {

    private final ServicoRepository servicoRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Servico> semCategoria = servicoRepository.findByCategoriaIsNull();
        semCategoria.forEach(servico -> servico.setCategoria(CategoriaServico.peloNome(servico.getNome())));
        servicoRepository.saveAll(semCategoria);
    }
}
