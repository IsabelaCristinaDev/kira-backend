# KIRA Backend

API REST da KIRA, plataforma de agendamento de serviços de beleza. Projeto desenvolvido como Trabalho de Conclusão de Curso (TCC) no SENAI FATESG.

- **Autoras:** Isabela Cristina e Wanessa Cardoso
- **Front-end:** [kira-platform](https://github.com/wanessacastr/kira-platform)

## Funcionalidades

- **Contas:** cadastro de cliente e de empresa, login com JWT, renovação de sessão e recuperação de senha por e-mail.
- **Vitrine:** busca de estúdios, portfólio e avaliações, abertos sem login.
- **Agendamentos:** criação, cancelamento e conclusão de atendimentos, com registro da forma de pagamento.
- **Gestão da empresa:** serviços, funcionárias, disponibilidade, bloqueios de horário, agenda, comissões e painel com indicadores.
- **Comunicação:** mensagens em tempo real por WebSocket, notificações do navegador e lembrete de agendamento por e-mail.
- **Cliente:** favoritas, contatos de emergência e foto de perfil.
- **Provador virtual:** simulação de resultado com IA (Cloudflare Workers AI), condicionada ao consentimento da usuária, que pode ser revogado.
- **Verificação de identidade:** compara a foto do documento com a selfie pelo kira-verify-ai e guarda só o resultado. Pode ser exigida para agendar e para aparecer na vitrine.
- **Privacidade (LGPD):** a conta logada baixa uma cópia dos próprios dados (`GET /api/perfil/dados`) e pode excluir a conta confirmando a senha (`DELETE /api/perfil`). A exclusão anonimiza os dados pessoais e mantém o histórico de atendimentos com o nome "Conta excluída".

> O pagamento é feito no estúdio. O sistema só registra a forma (dinheiro, cartão de crédito ou de débito, ou Pix), informada ao agendar ou ao concluir o atendimento. Não há integração com gateway de pagamento.

## Perfis de usuário

| Perfil | Descrição |
|---|---|
| `CLIENTE` | Busca estúdios, agenda serviços, avalia atendimentos e conversa com as empresas. |
| `EMPRESA` | Gerencia o próprio negócio. Pode ser do tipo `ESTABELECIMENTO` ou `AUTONOMA`. |
| `ADMIN` | Perfil previsto. As rotas `/api/admin/**` já são restritas a ele, mas ainda não há endpoints administrativos. |

## Tecnologias

- Java 17 e Spring Boot 3.3.6 (Web, Data JPA, Security, Validation, WebSocket, Mail, Actuator)
- PostgreSQL 16
- JWT (JJWT 0.12.3)
- Spring Retry
- springdoc-openapi (Swagger)
- Docker e Docker Compose
- JUnit 5, Mockito e Testcontainers
- JaCoCo, SonarCloud e GitHub Actions

## Como rodar

O projeto roda localmente. Não há ambiente de produção ativo.

**Pré-requisitos:** JDK 17 ou mais recente e Docker.

```bash
# 1. Criar o arquivo de configuração e preencher os valores
cp .env.example .env

# 2. Subir o PostgreSQL (porta 5433)
docker compose up -d

# 3. Subir a API (porta 8081)
./mvnw spring-boot:run
```

No Windows, use `mvnw.cmd` no lugar de `./mvnw`.

Com a API no ar:

- Swagger: <http://localhost:8081/swagger-ui.html>
- Status da aplicação: <http://localhost:8081/actuator/health>

As tabelas são criadas e atualizadas automaticamente pelo Hibernate (`ddl-auto: update`).

Para usar a verificação de identidade e a ordem do Provador, suba também o kira-ai (outro repositório, na pasta `../kira-ai`):

```bash
docker compose --profile ia up -d
```

- Ele fica em `http://localhost:8001`, o endereço padrão de `KIRA_AI_URL` e `VERIFY_SERVICE_URL`, e só a própria máquina acessa.
- Na primeira vez, a imagem (cerca de 12 GB) é montada. Para montar de novo depois de atualizar o kira-ai, acrescente `--build`.
- Sem o kira-ai, a API continua no ar: o Provador usa a própria ordem e a verificação responde que está indisponível.

### Banco para o RAG do kira-ai

O PostgreSQL do `docker-compose.yml` é montado com a extensão pgvector (`docker/postgres/Dockerfile`). O script `docker/postgres/rag.sql` prepara o acesso do kira-ai: cria o usuário `kira_rag`, o schema `rag` (dele) e as views de leitura em `kira_leitura` (estúdios, horários e serviços).

```bash
docker exec -i kira_postgres psql -U <DB_USERNAME> -d <DB_NAME> -v ON_ERROR_STOP=1 -1 < docker/postgres/rag.sql
docker exec -it kira_postgres psql -U <DB_USERNAME> -d <DB_NAME> -c '\password kira_rag'
```

- Rode o script depois de a API ter subido ao menos uma vez, para as tabelas existirem. Ele pode ser rodado de novo sem erro. A senha do `kira_rag` não fica em nenhum arquivo.
- Sem a tabela de suspensão de perfil (canal de segurança), a view de estúdios é criada sem esse filtro e o script avisa. Rode-o de novo quando a tabela existir.
- As views leem de funções, e não direto das tabelas, para deixar livres as colunas que o Hibernate ajusta quando a API sobe. Se uma entidade renomear ou remover uma coluna usada pelo script, a leitura do RAG dá erro até o `rag.sql` ser ajustado e rodado de novo.

### Rodar num servidor

O arquivo `docker-compose.servidor.yml` sobe a API, o banco e o MinIO (fotos) juntos, cada um no seu container:

```bash
cp .env.example .env   # preencher com os valores do servidor
docker compose -f docker-compose.servidor.yml up -d --build
```

- O banco e o MinIO não ficam acessíveis de fora do servidor: só a API os enxerga. As fotos saem para o app pela API.
- A API fica em `127.0.0.1:8081`, para ser publicada por um proxy com HTTPS. Para testar sem proxy, use `API_ENDERECO=0.0.0.0:8081`.
- O banco e as fotos enviadas ficam em volumes do Docker (`kira-servidor_kira_db_dados` e `kira-servidor_kira_minio_dados`) e continuam lá a cada novo deploy. Só `docker compose down -v` os apaga.
- As fotos vão sempre para o MinIO do compose, criado com as chaves `KIRA_S3_ACCESS_KEY` e `KIRA_S3_SECRET_KEY` do `.env`. O bucket é criado na primeira subida da API.
- O projeto se chama `kira-servidor`: rodando este arquivo na mesma máquina do `docker-compose.yml` (para ensaiar antes de subir), os containers e os volumes dos dois não se misturam.
- Além das variáveis obrigatórias da tabela abaixo, o servidor exige `KIRA_CORS_ORIGENS` e `FRONTEND_URL` com o endereço real do app web. As `SPRING_DATASOURCE_*` não são usadas: a API aponta sozinha para o banco do compose.

#### Com o kira-ai (opcional)

O kira-ai (verificação de identidade e ordem do Provador) fica em outro repositório e só sobe com o perfil `ia`. Ele é montado a partir da pasta do kira-ai, que por padrão fica ao lado desta (`../kira-ai`):

```bash
docker compose -f docker-compose.servidor.yml --profile ia up -d --build
```

- A API passa a usar o kira-ai do compose sozinha: `KIRA_AI_URL` e `VERIFY_SERVICE_URL` são ignorados no servidor.
- Sem o perfil, a API continua no ar: o Provador usa a própria ordem e a verificação responde que está indisponível. Nesse caso, deixe `VERIFICACAO_OBRIGATORIA=false`.
- Como o banco, o kira-ai não fica acessível de fora do servidor.
- A imagem tem cerca de 12 GB, e a IA usa até 3 GB de memória (`KIRA_AI_MEMORIA`, padrão `3g`). Se passar disso, só ela é reiniciada. Com o kira-ai, o servidor precisa de pelo menos 8 GB de memória e 40 GB de disco; sem ele, 4 GB bastam.
- Outra pasta para o kira-ai: `KIRA_AI_PASTA=/caminho/do/kira-ai`.

### Levar os dados locais para o servidor (primeira subida)

O banco e as fotos andam juntos: o banco guarda só o nome de cada foto, e o arquivo fica no MinIO. Por isso o dump é gerado na hora, junto com as fotos daquele momento. Um dump antigo aponta para fotos que podem não existir mais.

**1. Na máquina local**, gerar o dump do banco atual (fica fora do Git pelo `.gitignore`):

```bash
docker exec kira_postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > kira.dump
```

E exportar as fotos do MinIO local para a pasta `fotos-minio/` (também fora do Git):

```bash
docker exec kira_minio sh -c 'mc alias set origem http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mirror --quiet origem/kira-arquivos /tmp/fotos-minio'
docker cp kira_minio:/tmp/fotos-minio ./fotos-minio
docker exec kira_minio rm -rf /tmp/fotos-minio
```

Copiar para o servidor o `kira.dump` e a pasta `fotos-minio/`, junto com o projeto.

**2. No servidor**, com o `.env` preenchido. A ordem importa: o banco é restaurado **antes** de a API subir, senão o Hibernate cria tabelas vazias e a restauração falha.

```bash
DC="docker compose -f docker-compose.servidor.yml"

# Sobe só o banco
$DC up -d --wait postgres

# Restaura. --no-owner: o usuário do banco no servidor pode ter outro nome
$DC cp kira.dump postgres:/tmp/kira.dump
$DC exec -T postgres sh -c 'pg_restore --no-owner --no-privileges --single-transaction --exit-on-error -U "$POSTGRES_USER" -d "$POSTGRES_DB" /tmp/kira.dump && rm /tmp/kira.dump'

# Apaga as sessões do ambiente local: um refresh token antigo continuaria valendo no servidor
$DC exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "TRUNCATE refresh_token, token_recuperacao_senha, dispositivo_notificacao"'

# Sobe a API (ela cria o bucket) e copia as fotos para o MinIO
$DC up -d --wait --build
$DC cp fotos-minio/. minio:/tmp/fotos-minio/
$DC exec -T minio sh -c 'mc alias set destino http://localhost:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && mc mirror --quiet /tmp/fotos-minio destino/kira-arquivos && rm -rf /tmp/fotos-minio'

# Confere: a quantidade tem que ser igual à de arquivos em fotos-minio/ (find fotos-minio -type f | wc -l)
$DC exec -T minio sh -c 'mc ls --recursive destino/kira-arquivos | wc -l'
```

Se o banco do servidor já tiver dados, a restauração para no primeiro erro e não altera nada. Depois de conferir, apague do servidor o `kira.dump` e a pasta `fotos-minio/`: eles têm os dados e as fotos de todos os usuários.

## Variáveis de ambiente

O arquivo `.env` é carregado automaticamente e nunca deve ir para o repositório. O modelo completo está em [`.env.example`](.env.example).

| Grupo | Variáveis | Obrigatório? |
|---|---|---|
| Banco (Docker) | `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME` | Sim |
| Banco (aplicação) | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Sim |
| JWT | `JWT_SECRET`, `JWT_REFRESH_EXPIRATION` | Sim |
| E-mail | `MAIL_USERNAME`, `MAIL_PASSWORD` | Para enviar e-mails |
| Notificações do navegador | `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` | Para usar as notificações |
| Provador virtual | `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN` | Para usar o provador |
| Verificação de identidade | `VERIFY_SERVICE_URL` (padrão `http://localhost:8001`, o kira-ai) e `VERIFY_SERVICE_KEY` (opcional) | Para usar a verificação |
| Exigir a verificação | `VERIFICACAO_OBRIGATORIA` | Não, o padrão é `false` |
| Servidor e front-end | `SERVER_PORT`, `FRONTEND_URL` | Não, têm valor padrão |
| Log do SQL | `MOSTRAR_SQL` | Não, o padrão é `false` |
| Fotos enviadas | `KIRA_ARQUIVOS_TIPO`, `KIRA_S3_*` (MinIO ou R2) ou `KIRA_ARQUIVOS_PASTA` | Não, o padrão é a pasta `./uploads` (ver abaixo) |
| Lembrete | `LEMBRETE_ANTECEDENCIA_HORAS`, `LEMBRETE_CRON`, `LEMBRETE_RETRY_*` | Não, têm valor padrão |

### Fotos enviadas

O banco guarda só o nome de cada foto; o arquivo fica num armazenamento de objetos compatível com S3, escolhido por `KIRA_ARQUIVOS_TIPO=s3`:

- **Local:** o MinIO do `docker-compose.yml`, com as chaves `KIRA_S3_ACCESS_KEY` e `KIRA_S3_SECRET_KEY` do `.env`. Painel em <http://localhost:9001>.
- **Servidor:** o MinIO do `docker-compose.servidor.yml`, já configurado nele (ver "Rodar num servidor").
- **Mais adiante, no servidor:** o Cloudflare R2, que guarda as próprias cópias e não cobra pela saída das fotos. A troca não muda código nem o banco, só onde os arquivos ficam. No painel da Cloudflare, em R2:
  1. crie o bucket `kira-arquivos`, sem acesso público (as fotos saem pela API);
  2. em *Manage API tokens*, crie um token com **Object Read & Write** só nesse bucket;
  3. copie as fotos do MinIO para o R2 (`mc mirror` ou `rclone sync`) e confira a quantidade;
  4. no `docker-compose.servidor.yml`, tire o serviço `minio` (e o `depends_on` dele) e troque, na API, `KIRA_S3_ENDPOINT` por `https://<ID da conta>.r2.cloudflarestorage.com` e `KIRA_S3_REGIAO` por `auto`;
  5. no `.env` do servidor, coloque as chaves do token em `KIRA_S3_ACCESS_KEY`/`KIRA_S3_SECRET_KEY`.

Sem `KIRA_ARQUIVOS_TIPO`, as fotos ficam na pasta `KIRA_ARQUIVOS_PASTA` (padrão `./uploads`).

#### Backup das fotos

Faça o backup das fotos junto com o do banco, no mesmo momento: um sem o outro deixa fotos sem registro ou registros sem foto.

- **R2:** os dados ficam na Cloudflare, sem volume para copiar. Para ter uma cópia fora dela, use `mc mirror` ou `rclone sync` do bucket para uma pasta.
- **MinIO:** as fotos ficam no volume `kira-backend_kira_minio_data` (local) ou `kira-servidor_kira_minio_dados` (servidor, onde os comandos abaixo levam `-f docker-compose.servidor.yml`). Com o MinIO parado, para a cópia ficar consistente:

```bash
docker compose stop minio
docker run --rm -v kira-backend_kira_minio_data:/data:ro -v "$PWD":/backup alpine \
  tar czf /backup/kira-minio.tar.gz -C /data .
docker compose start minio
```

Para restaurar num volume vazio, com o MinIO parado: o mesmo comando, sem o `:ro` no volume e com `tar xzf /backup/kira-minio.tar.gz -C /data`.

### Exigir a identidade verificada

Com `VERIFICACAO_OBRIGATORIA=true`:

- a cliente sem verificação não agenda: a API responde `403` com o código `VERIFICACAO_OBRIGATORIA`;
- o estúdio sem verificação sai da vitrine, da busca e do ranking do provador, e não recebe agendamento (`400`). O perfil dele continua abrindo pelo id;
- `GET /api/verificacao/me` devolve `"required": true`, para o app saber quando pedir a verificação.

Com `false`, o padrão, nada disso vale, e o backend avisa no log ao subir. Só ligue com o kira-verify-ai no ar: sem ele, ninguém consegue se verificar.

Para gerar uma chave JWT forte:

```bash
openssl rand -hex 64
```

## Arquitetura

O código fica em `br.com.kira.kirabackend`, organizado em camadas:

| Pacote | Responsabilidade |
|---|---|
| `controller` | Endpoints REST. Recebem Request e devolvem Response, sem regra de negócio. |
| `service` | Regras de negócio e transações. |
| `service/strategy` | Regras que variam conforme o contexto. |
| `repository` | Acesso a dados com Spring Data JPA. |
| `domain` | Entidades JPA e enums. |
| `dto` | Objetos de entrada (`request`) e saída (`response`) da API. |
| `security` | Autenticação JWT e controle de acesso a recursos. |
| `observer` | Eventos de domínio e seus listeners. |
| `job` | Tarefas agendadas, como o lembrete de agendamento. |
| `exception` | Exceções de negócio e tratamento global de erros. |
| `config` | Configurações do Spring, segurança e WebSocket. |

### Padrões de projeto

- **Strategy:** o cancelamento segue regras diferentes para cada lado. A cliente precisa de no mínimo 24 horas de antecedência para cancelar ou remarcar, e a empresa não tem prazo. Cada regra fica em uma classe, então uma regra nova não altera as existentes.
- **Observer:** efeitos colaterais de um agendamento ficam em listeners de eventos, separados do fluxo principal. A comissão, por exemplo, é gerada ao tratar o evento `AtendimentoConcluidoEvent`.
- **DTO:** nenhuma entidade JPA sai do controller. Assim, a API não expõe dados internos, e a entrada é validada com Bean Validation.

## Segurança

- A API é stateless e autentica pelo header `Authorization: Bearer <token>`.
- As rotas exigem login por padrão. As públicas são declaradas uma a uma em `SecurityConfig`.
- Sem token ou com token vencido, a resposta é `401`. Logado e sem permissão, é `403`.
- O acesso a recursos de outra empresa ou de outra cliente é bloqueado nos services.
- As senhas são guardadas com BCrypt, e os segredos ficam apenas no `.env`.

### Limite de tentativas de login

Os números ficam em `kira.security.rate-limit` no `application.yml`. Os contadores ficam em memória e zeram quando a API reinicia.

| Chave | Regra | Resposta |
|---|---|---|
| IP + conta | 5 senhas erradas em 15 min | `429` com `Retry-After`, só para esse IP nessa conta |
| IP | 20 senhas erradas em 15 min, em qualquer conta | `429` com `Retry-After` |
| Conta | a partir de 5 falhas: atraso de 1 s, 2 s, 4 s, até 8 s, e verificação extra | a conta nunca é bloqueada, para ninguém conseguir trancar a conta de outra pessoa |
| Conta | 10 falhas | e-mail de aviso ao dono, no máximo 1 por hora |

- Login certo zera as falhas daquele IP naquela conta.
- E-mail sem conta, senha errada e conta inativa recebem a mesma resposta (`401`, "E-mail ou senha inválidos") e levam o mesmo tempo, porque o BCrypt roda nos três casos.
- A verificação extra é a interface `VerificacaoHumana`. Hoje ela sempre aprova. Para usar um CAPTCHA, basta criar outra implementação.
- O log registra o IP e o e-mail mascarado (`is***@gmail.com`), nunca a senha.

**Limitações conhecidas:**

- **O atraso progressivo ocupa uma thread durante a espera.** Cada login atrasado segura uma das threads do Tomcat (200 por padrão) por até 8 s. Um ataque vindo de muitos IPs, errando muitas contas ao mesmo tempo, poderia esgotá-las e deixar a API lenta para todos. O limite de 20 falhas por IP reduz bastante esse risco, mas não o elimina. O atraso acontece fora da transação, então não segura conexões do banco.
- **Os contadores ficam na memória de cada instância.** Com mais de uma instância da API, cada uma conta separado. Para um limite compartilhado, seria preciso um armazenamento comum, como o Redis.

**Pendência:**

- **CAPTCHA:** quando for integrado, a API deve devolver um código próprio (por exemplo, `CAPTCHA_OBRIGATORIO`) quando a verificação extra for exigida, para o front saber que precisa mostrar o desafio. Hoje ela responderia o mesmo `401` da senha errada. O ponto exato está marcado com `TODO(CAPTCHA)` no `ProtecaoLogin`.

### Recuperação de senha

- O pedido (`POST /api/auth/recuperar-senha`) sempre responde `200` com "Se o e-mail estiver cadastrado, enviaremos as instruções.", no mesmo tempo, exista a conta ou não. A busca da conta e o envio rodam em segundo plano.
- Limites: 3 pedidos por hora por e-mail e 10 por hora por IP. A troca de senha (`POST /api/auth/redefinir-senha`) aceita 10 tentativas a cada 15 min por IP. Passou disso, `429` com `Retry-After`.
- O token tem 32 bytes do `SecureRandom`, vale 15 minutos e só pode ser usado uma vez. O banco guarda apenas o SHA-256 dele. Um pedido novo invalida os anteriores.
- Ao redefinir, todas as sessões são encerradas: os refresh tokens são apagados, e o JWT emitido antes da troca (`senhaAlteradaEm`) deixa de valer na API e no WebSocket. O chat que estiver aberto é fechado na hora.

### Diagnóstico de IP (primeiro deploy)

Atrás de um proxy, o IP que chega à API é o do proxy. O `FORWARD_HEADERS_STRATEGY` diz ao Spring como achar o IP real, e o valor certo depende do proxy. O `X-Forwarded-For` nunca é lido à mão, porque o cliente consegue inventá-lo.

1. Suba com `FORWARD_HEADERS_STRATEGY=none` e `REGISTRAR_HEADERS_IP=true`. Com `framework`, o Spring apaga os headers antes do log, e o diagnóstico não mostra nada.
2. Faça alguns logins e veja no log as linhas `Diagnóstico de IP`.
3. Escolha a estratégia (`framework` usa o primeiro IP do `X-Forwarded-For`; `native` usa o último que não é de rede interna). Confirme que um `X-Forwarded-For` inventado pelo cliente não muda o IP usado.
4. **Desligue o `REGISTRAR_HEADERS_IP` e remova o código marcado com `TODO(LGPD)`**: IP é dado pessoal.

## Limitações conhecidas

### Datas sem fuso horário

- **Como está hoje:** as datas são gravadas no horário de Brasília (`LocalDateTime.now(KiraTimeZone.DEFAULT)`), sem a informação de fuso, em colunas `timestamp without time zone` (25 colunas). O Postgres roda em UTC.
- **Por que funciona:** toda gravação e leitura passa pela API, que sempre usa o horário de Brasília.
- **Onde atrapalha:**
  - SQL escrito à mão: o `now()` do banco está 3 horas à frente das datas gravadas (por exemplo, `now() - interval '1 minute'` vira uma data no futuro para a API);
  - relatórios e integrações precisam saber que as datas estão em horário de Brasília;
  - se o horário de verão voltar, a hora repetida na virada fica ambígua.
- **Para avaliar no futuro:** usar `Instant` no código e `timestamptz` no banco, com `hibernate.jdbc.time_zone=UTC`. Os dados existentes seriam convertidos com `ALTER COLUMN ... TYPE timestamptz USING coluna AT TIME ZONE 'America/Sao_Paulo'`.

As limitações do limite de tentativas de login estão na seção [Segurança](#segurança).

## Testes

```bash
./mvnw test
```

- **Unitários:** services, strategies, segurança e job, com JUnit 5 e Mockito.
- **Integração:** os testes do pacote `integracao` usam um PostgreSQL real, criado pelo Testcontainers. Sem Docker, eles são ignorados.
- **`KiraBackendApplicationTests`:** confere que a aplicação inteira sobe, com um PostgreSQL do Testcontainers. Não precisa do banco local nem das variáveis da máquina; sem Docker, é ignorado.

O relatório de cobertura do JaCoCo é gerado em `target/site/jacoco/index.html`. O GitHub Actions roda os testes em cada pull request e em cada push na `main`.
