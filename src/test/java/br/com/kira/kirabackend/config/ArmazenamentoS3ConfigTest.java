package br.com.kira.kirabackend.config;

import br.com.kira.kirabackend.service.ArmazenamentoArquivos;
import br.com.kira.kirabackend.service.ArmazenamentoLocal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmazenamentoS3ConfigTest {

    @TempDir
    Path pasta;

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ArmazenamentoS3Config.class, ArmazenamentoLocal.class)
                .withPropertyValues(
                        "kira.arquivos.pasta=" + pasta,
                        "kira.arquivos.s3.endpoint=http://localhost:9000",
                        "kira.arquivos.s3.regiao=us-east-1",
                        "kira.arquivos.s3.bucket=kira-teste",
                        "kira.arquivos.s3.access-key=",
                        "kira.arquivos.s3.secret-key=");
    }

    @Test
    @DisplayName("Sem KIRA_ARQUIVOS_TIPO, as fotos ficam na pasta local")
    void padraoEhPastaLocal() {
        contexto().run(app -> assertInstanceOf(ArmazenamentoLocal.class, app.getBean(ArmazenamentoArquivos.class)));
    }

    @Test
    @DisplayName("Com o tipo s3 e sem as chaves, a aplicação não sobe e diz quais variáveis faltam")
    void s3SemChavesNaoSobe() {
        contexto().withPropertyValues("kira.arquivos.tipo=s3").run(app -> {
            assertTrue(app.getStartupFailure() != null);
            String mensagens = String.valueOf(rootCause(app.getStartupFailure()).getMessage());
            assertTrue(mensagens.contains("KIRA_S3_ACCESS_KEY"), mensagens);
        });
    }

    private static Throwable rootCause(Throwable erro) {
        Throwable atual = erro;
        while (atual.getCause() != null) {
            atual = atual.getCause();
        }
        return atual;
    }
}
