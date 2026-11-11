package br.com.kira.kirabackend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;

/**
 * Confere que a aplicação inteira sobe. Usa um Postgres descartável, como os testes com banco real,
 * então não depende do banco local nem das variáveis da máquina. Sem Docker, este teste é ignorado.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class KiraBackendApplicationTests {

    @Container
    private static final PostgreSQLContainer<?> BANCO = new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    private static Path pastaDeArquivos;

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", BANCO::getJdbcUrl);
        registro.add("spring.datasource.username", BANCO::getUsername);
        registro.add("spring.datasource.password", BANCO::getPassword);
        registro.add("jwt.secret", () -> "segredo-usado-so-neste-teste-".repeat(4));
        registro.add("jwt.refresh-expiration", () -> "604800000");
        registro.add("kira.arquivos.pasta", () -> pastaDeArquivos.toString());
    }

    @Test
    void contextLoads() {
    }

}
