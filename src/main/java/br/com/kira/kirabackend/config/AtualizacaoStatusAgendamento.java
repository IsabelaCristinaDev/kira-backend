package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.domain.enums.StatusAgendamento;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * O Hibernate cria a coluna status com uma regra CHECK que aceita só os status que existiam na criação da
 * tabela, e o ddl-auto: update não atualiza essa regra. Sem isto, um banco já existente recusa um status
 * novo (como CHEGADA_CONFIRMADA) com erro 500. Na subida, a regra é refeita com todos os status do enum,
 * e só quando falta algum.
 */
@RequiredArgsConstructor
@Component
public class AtualizacaoStatusAgendamento implements ApplicationRunner {

    static final String REGRA = "agendamento_status_check";

    private static final Logger log = LoggerFactory.getLogger(AtualizacaoStatusAgendamento.class);

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> definicao = jdbc.queryForList(
                "select pg_get_constraintdef(oid) from pg_constraint"
                        + " where conrelid = 'agendamento'::regclass and conname = ?",
                String.class, REGRA);
        if (!definicao.isEmpty() && aceitaTodos(definicao.get(0))) {
            return;
        }
        // Os nomes vêm do enum, não de quem usa a API: montar o texto aqui não abre espaço para injeção
        String status = Arrays.stream(StatusAgendamento.values())
                .map(s -> "'" + s.name() + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute("alter table agendamento drop constraint if exists " + REGRA);
        jdbc.execute("alter table agendamento add constraint " + REGRA + " check (status in (" + status + "))");
        log.info("Regra {} atualizada com os status {}", REGRA, status);
    }

    private static boolean aceitaTodos(String definicao) {
        return Arrays.stream(StatusAgendamento.values()).allMatch(s -> definicao.contains("'" + s.name() + "'"));
    }
}
