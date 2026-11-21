package br.com.kira.kirabackend.integracao;

import br.com.kira.kirabackend.config.ArmazenamentoS3Config;
import br.com.kira.kirabackend.service.ArmazenamentoArquivos;
import br.com.kira.kirabackend.service.ArmazenamentoS3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.io.IOException;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * O armazenamento das fotos contra um MinIO de verdade, com a mesma imagem e o mesmo comando do docker-compose.yml.
 * O cliente vem de {@link ArmazenamentoS3Config}, então a configuração usada em produção também é testada.
 * Sem Docker, o teste é ignorado (o CI confere que ele rodou).
 */
@Testcontainers(disabledWithoutDocker = true)
class ArmazenamentoS3ComMinioRealTest {

    // Credenciais só deste container descartável
    private static final String USUARIO = "kira-teste";
    private static final String SENHA = "kira-teste-senha";
    private static final int PORTA_S3 = 9000;

    @Container
    private static final GenericContainer<?> MINIO = new GenericContainer<>("pgsty/minio:RELEASE.2026-08-04T00-00-00Z")
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", USUARIO)
            .withEnv("MINIO_ROOT_PASSWORD", SENHA)
            .withExposedPorts(PORTA_S3)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(PORTA_S3));

    private static S3Client s3;
    private static ArmazenamentoS3 armazenamento;

    @BeforeAll
    static void conectar() {
        s3 = cliente(SENHA);
        armazenamento = new ArmazenamentoS3(s3, "kira-arquivos");
    }

    @AfterAll
    static void fechar() {
        s3.close();
    }

    private static S3Client cliente(String senha) {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(PORTA_S3);
        return new ArmazenamentoS3Config().s3Client(endpoint, "us-east-1", USUARIO, senha);
    }

    private static byte[] bytesAleatorios(int tamanho) {
        byte[] conteudo = new byte[tamanho];
        new Random(42).nextBytes(conteudo);
        return conteudo;
    }

    private static byte[] ler(Optional<Resource> arquivo) throws IOException {
        assertTrue(arquivo.isPresent(), "o arquivo deveria existir no bucket");
        return arquivo.get().getContentAsByteArray();
    }

    @Test
    @DisplayName("Bucket que não existe é criado na subida")
    void criaBucket() {
        String bucket = "novo-" + UUID.randomUUID();

        new ArmazenamentoS3(s3, bucket);

        assertDoesNotThrow(() -> s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build()));
    }

    @Test
    @DisplayName("Subir de novo com o bucket já criado não dá erro")
    void bucketExistente() {
        assertDoesNotThrow(() -> new ArmazenamentoS3(s3, "kira-arquivos"));
    }

    @Test
    @DisplayName("Foto pequena: guarda com nome aleatório e devolve os mesmos bytes")
    void guardaEAbreFotoPequena() throws IOException {
        byte[] foto = bytesAleatorios(2_000);

        String nome = armazenamento.salvar(foto, "png");

        assertTrue(ArmazenamentoArquivos.nomeValido(nome), "nome gerado fora do padrão: " + nome);
        assertTrue(nome.endsWith(".png"));
        assertArrayEquals(foto, ler(armazenamento.abrir(nome)));
    }

    @Test
    @DisplayName("Foto de 5 MB, o limite: chega inteira ao MinIO e volta igual")
    void guardaEAbreFotoNoLimite() throws IOException {
        byte[] foto = bytesAleatorios(5 * 1024 * 1024);

        String nome = armazenamento.salvar(foto, "jpg");

        assertArrayEquals(foto, ler(armazenamento.abrir(nome)));
    }

    @Test
    @DisplayName("Nome válido que não existe no bucket: nada a devolver")
    void abreInexistente() {
        assertTrue(armazenamento.abrir(UUID.randomUUID() + ".png").isEmpty());
    }

    @Test
    @DisplayName("Apagar remove a foto, e apagar o que não existe não dá erro")
    void apaga() {
        String nome = armazenamento.salvar(bytesAleatorios(100), "png");

        armazenamento.apagar(nome);

        assertTrue(armazenamento.abrir(nome).isEmpty());
        assertDoesNotThrow(() -> armazenamento.apagar(nome));
    }

    @Test
    @DisplayName("Com a senha errada, a aplicação não sobe e a mensagem cita as variáveis")
    void senhaErrada() {
        try (S3Client comSenhaErrada = cliente("senha-errada")) {
            IllegalStateException erro = assertThrows(IllegalStateException.class,
                    () -> new ArmazenamentoS3(comSenhaErrada, "kira-arquivos"));

            assertTrue(erro.getMessage().contains("KIRA_S3_ENDPOINT"), erro.getMessage());
        }
    }
}
