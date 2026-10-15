package br.com.kira.kirabackend.domain.entity;

import jakarta.persistence.Embedded;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O Hibernate ignora campos marcados com a palavra-chave {@code transient}.
 * Se o endereço voltar a ser transient, ele deixa de ser salvo no banco.
 */
class EnderecoPersistenciaTest {

    @ParameterizedTest
    @ValueSource(classes = {Empresa.class, Cliente.class})
    @DisplayName("O endereço é persistido junto com o usuário")
    void enderecoEhPersistido(Class<?> entidade) throws NoSuchFieldException {
        Field endereco = entidade.getDeclaredField("endereco");

        assertFalse(Modifier.isTransient(endereco.getModifiers()),
                entidade.getSimpleName() + ".endereco não pode ser transient");
        assertFalse(endereco.isAnnotationPresent(Transient.class));
        assertNotNull(endereco.getAnnotation(Embedded.class));
    }

    @Test
    @DisplayName("Endereco é Serializable, já que Usuario (UserDetails) é Serializable")
    void enderecoEhSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(Endereco.class));
    }
}
