package br.com.kira.kirabackend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ArmazenamentoS3Test {

    private static final String BUCKET = "kira-teste";
    private static final String NOME = "123e4567-e89b-12d3-a456-426614174000.png";

    private final S3Client s3 = mock(S3Client.class);

    @Test
    @DisplayName("Bucket que já existe não é criado de novo")
    void bucketExistente() {
        new ArmazenamentoS3(s3, BUCKET);

        verify(s3).headBucket(HeadBucketRequest.builder().bucket(BUCKET).build());
        verify(s3, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("Bucket que não existe é criado na subida")
    void criaBucket() {
        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.builder().build());

        new ArmazenamentoS3(s3, BUCKET);

        verify(s3).createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Test
    @DisplayName("Com o MinIO fora do ar, a aplicação não sobe e a mensagem cita as variáveis")
    void minioForaDoAr() {
        when(s3.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(SdkClientException.create("Connection refused"));

        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> new ArmazenamentoS3(s3, BUCKET));

        assertTrue(erro.getMessage().contains("KIRA_S3_ENDPOINT"));
        assertTrue(erro.getMessage().contains(BUCKET));
    }

    @Test
    @DisplayName("Salva no bucket com nome aleatório e a extensão pedida")
    void salva() throws IOException {
        ArmazenamentoS3 armazenamento = new ArmazenamentoS3(s3, BUCKET);
        ArgumentCaptor<PutObjectRequest> pedido = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> corpo = ArgumentCaptor.forClass(RequestBody.class);

        String nome = armazenamento.salvar(new byte[]{1, 2, 3}, "png");

        verify(s3).putObject(pedido.capture(), corpo.capture());
        assertTrue(ArmazenamentoArquivos.nomeValido(nome));
        assertTrue(nome.endsWith(".png"));
        assertEquals(BUCKET, pedido.getValue().bucket());
        assertEquals(nome, pedido.getValue().key());
        assertArrayEquals(new byte[]{1, 2, 3},
                corpo.getValue().contentStreamProvider().newStream().readAllBytes());
    }

    @Test
    @DisplayName("Abre o conteúdo guardado no bucket")
    void abre() throws IOException {
        ArmazenamentoS3 armazenamento = new ArmazenamentoS3(s3, BUCKET);
        when(s3.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(NOME).build()))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{4, 5}));

        byte[] conteudo = armazenamento.abrir(NOME).orElseThrow().getContentAsByteArray();

        assertArrayEquals(new byte[]{4, 5}, conteudo);
    }

    @Test
    @DisplayName("Arquivo que não existe no bucket não abre")
    void arquivoInexistente() {
        ArmazenamentoS3 armazenamento = new ArmazenamentoS3(s3, BUCKET);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

        assertTrue(armazenamento.abrir(NOME).isEmpty());
    }

    @Test
    @DisplayName("Apagar remove do bucket")
    void apaga() {
        ArmazenamentoS3 armazenamento = new ArmazenamentoS3(s3, BUCKET);

        armazenamento.apagar(NOME);

        verify(s3).deleteObject(DeleteObjectRequest.builder().bucket(BUCKET).key(NOME).build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../segredo.png", "..", "a/b.png", "segredo.png", ""})
    @DisplayName("Nome fora do padrão não chega ao bucket")
    void nomeForaDoPadrao(String nome) {
        ArmazenamentoS3 armazenamento = new ArmazenamentoS3(s3, BUCKET);
        verify(s3).headBucket(any(HeadBucketRequest.class));

        assertTrue(armazenamento.abrir(nome).isEmpty());
        armazenamento.apagar(nome);

        verifyNoMoreInteractions(s3);
    }
}
