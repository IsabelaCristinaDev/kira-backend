package br.com.kira.kirabackend.service;

import br.com.kira.kirabackend.domain.entity.Cliente;
import br.com.kira.kirabackend.domain.entity.Empresa;
import br.com.kira.kirabackend.domain.entity.Endereco;
import br.com.kira.kirabackend.domain.enums.Genero;
import br.com.kira.kirabackend.domain.enums.TipoEstabelecimento;
import br.com.kira.kirabackend.dto.request.ClientePerfilRequest;
import br.com.kira.kirabackend.dto.request.EmpresaPerfilRequest;
import br.com.kira.kirabackend.dto.request.EnderecoRequest;
import br.com.kira.kirabackend.dto.response.ClientePerfilResponse;
import br.com.kira.kirabackend.dto.response.EmpresaPerfilResponse;
import br.com.kira.kirabackend.exception.RecursoNaoEncontradoException;
import br.com.kira.kirabackend.exception.RegraDeNegocioException;
import br.com.kira.kirabackend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PerfilService service;

    private static final EnderecoRequest ENDERECO = new EnderecoRequest(
            "01001000", "Praça da Sé", "10", "Sala 2", "Sé", "São Paulo", "SP");

    @Nested
    @DisplayName("Perfil da cliente")
    class PerfilCliente {

        private Cliente cliente;

        @BeforeEach
        void setUp() {
            cliente = new Cliente();
            cliente.setId(UUID.randomUUID());
            cliente.setNome("Ana");
            cliente.setEmail("ana@kira.com");
            cliente.setCpf("12345678900");
            cliente.setTelefone("11999990000");
            Endereco antigo = new Endereco();
            antigo.setCidade("Campinas");
            cliente.setEndereco(antigo);
        }

        private ClientePerfilRequest request(EnderecoRequest endereco) {
            return new ClientePerfilRequest("  Ana Souza ", " 11988887777 ", LocalDate.of(1995, 3, 10),
                    Genero.values()[0], endereco, -23.55, -46.63);
        }

        @Test
        @DisplayName("Devolve o próprio perfil com e-mail e CPF")
        void obter() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));

            ClientePerfilResponse r = service.obterCliente(cliente.getId(), cliente.getId());

            assertEquals("ana@kira.com", r.email());
            assertEquals("12345678900", r.cpf());
            assertEquals("Campinas", r.endereco().cidade());
        }

        @Test
        @DisplayName("Atualiza os campos editáveis e mantém e-mail e CPF")
        void atualizar() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

            ClientePerfilResponse r = service.atualizarCliente(cliente.getId(), cliente.getId(), request(ENDERECO));

            assertEquals("Ana Souza", r.nome());
            assertEquals("11988887777", r.telefone());
            assertEquals(LocalDate.of(1995, 3, 10), r.dataNascimento());
            assertEquals("São Paulo", r.endereco().cidade());
            assertEquals("01001000", r.endereco().cep());
            assertEquals(-23.55, r.latitude());
            assertEquals("ana@kira.com", r.email());
            assertEquals("12345678900", r.cpf());
            verify(usuarioRepository).save(cliente);
        }

        @Test
        @DisplayName("Endereço ausente apaga o endereço")
        void semEndereco() {
            when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));
            when(usuarioRepository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

            assertNull(service.atualizarCliente(cliente.getId(), cliente.getId(), request(null)).endereco());
        }

        @Test
        @DisplayName("Outro usuário não vê nem edita o perfil")
        void outroUsuario() {
            UUID clienteId = cliente.getId();
            UUID outro = UUID.randomUUID();
            ClientePerfilRequest request = request(ENDERECO);

            RegraDeNegocioException ex = assertThrows(RegraDeNegocioException.class,
                    () -> service.obterCliente(clienteId, outro));
            assertEquals("Você só pode acessar o seu próprio perfil", ex.getMessage());
            assertThrows(RegraDeNegocioException.class,
                    () -> service.atualizarCliente(clienteId, outro, request));
            verifyNoInteractions(usuarioRepository);
        }

        @Test
        @DisplayName("Empresa na rota de cliente dá 404")
        void empresaNaRotaDeCliente() {
            Empresa empresa = new Empresa();
            empresa.setId(UUID.randomUUID());
            UUID id = empresa.getId();
            when(usuarioRepository.findById(id)).thenReturn(Optional.of(empresa));

            assertThrows(RecursoNaoEncontradoException.class, () -> service.obterCliente(id, id));
        }
    }

    @Nested
    @DisplayName("Perfil da empresa")
    class PerfilEmpresa {

        private Empresa empresa;

        @BeforeEach
        void setUp() {
            empresa = new Empresa();
            empresa.setId(UUID.randomUUID());
            empresa.setNome("Studio Kira");
            empresa.setEmail("studio@kira.com");
            empresa.setCnpj("12345678000199");
            empresa.setEspecialidades("Unha, Cílios");
            empresa.setTipoEstabelecimento(TipoEstabelecimento.values()[0]);
        }

        private EmpresaPerfilRequest request(List<String> especialidades) {
            return new EmpresaPerfilRequest("Studio Kira Centro", "1133334444", "Nova descrição",
                    especialidades, null, ENDERECO, -23.55, -46.63, TipoEstabelecimento.values()[0]);
        }

        @Test
        @DisplayName("Devolve as especialidades como lista")
        void obter() {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));

            EmpresaPerfilResponse r = service.obterEmpresa(empresa.getId());

            assertEquals(List.of("Unha", "Cílios"), r.especialidades());
            assertEquals("12345678000199", r.cnpj());
        }

        @Test
        @DisplayName("Atualiza os campos editáveis, limpa as especialidades e mantém e-mail e CNPJ")
        void atualizar() {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(usuarioRepository.save(any(Empresa.class))).thenAnswer(i -> i.getArgument(0));

            EmpresaPerfilResponse r = service.atualizarEmpresa(empresa.getId(),
                    request(Arrays.asList(" Sobrancelha ", "", null, "Maquiagem")));

            assertEquals("Studio Kira Centro", r.nome());
            assertEquals("Nova descrição", r.descricao());
            assertEquals("Sobrancelha, Maquiagem", empresa.getEspecialidades());
            assertEquals(List.of("Sobrancelha", "Maquiagem"), r.especialidades());
            assertEquals("São Paulo", r.endereco().cidade());
            assertEquals("studio@kira.com", r.email());
            assertEquals("12345678000199", r.cnpj());
        }

        @Test
        @DisplayName("Lista de especialidades vazia apaga as especialidades")
        void semEspecialidades() {
            when(usuarioRepository.findById(empresa.getId())).thenReturn(Optional.of(empresa));
            when(usuarioRepository.save(any(Empresa.class))).thenAnswer(i -> i.getArgument(0));

            EmpresaPerfilResponse r = service.atualizarEmpresa(empresa.getId(), request(List.of()));

            assertNull(empresa.getEspecialidades());
            assertEquals(List.of(), r.especialidades());
        }

        @Test
        @DisplayName("Cliente na rota de empresa dá 404 e nada é salvo")
        void clienteNaRotaDeEmpresa() {
            Cliente cliente = new Cliente();
            cliente.setId(UUID.randomUUID());
            UUID id = cliente.getId();
            EmpresaPerfilRequest request = request(List.of());
            when(usuarioRepository.findById(id)).thenReturn(Optional.of(cliente));

            assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizarEmpresa(id, request));
            verify(usuarioRepository, never()).save(any());
        }
    }
}
