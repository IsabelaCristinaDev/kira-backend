# Cenários de teste manuais

Cenários validados com `curl` (ou Postman) contra a API rodando localmente, como pede a Definition of Done.
Complementam os testes automatizados (`./mvnw test`): aqui o objetivo é conferir a API de ponta a ponta,
com banco real, status code e corpo da resposta.

## Como preparar

1. Subir o banco: `docker compose up -d` (Postgres na porta 5433).
2. Subir a API: `./mvnw spring-boot:run` (porta 8081).
3. Criar as contas de teste pelas rotas públicas de cadastro:
   - `POST /api/auth/registro/empresa` (com pelo menos um serviço na lista `servicos`);
   - `POST /api/auth/registro/cliente` (duas clientes, para testar o acesso de uma aos dados da outra).
4. Fazer login em `POST /api/auth/login` e guardar o `id` e o `token` de cada conta.
5. Abrir a agenda do estúdio em `PUT /api/empresas/{empresaId}/disponibilidade`.

Nas rotas protegidas, enviar os headers:

```
Authorization: Bearer <token>
Content-Type: application/json
```

Convenção da coluna **Resultado**: data da última execução e se bateu com o esperado.

---

## Valor no agendamento

O agendamento guarda o preço do serviço no momento em que é criado (`valor`). Se o estúdio mudar o preço
depois, os agendamentos já feitos mantêm o valor combinado. Agendamentos criados antes desta mudança
têm `valor` nulo.

Pré-condição: estúdio com o serviço "Corte" a R$ 80,00 e agenda aberta; clientes Ana e Bia cadastradas.

| ID | Cenário | Request | Esperado | Resultado |
|---|---|---|---|---|
| VAL-01 | Agendar sem token | `POST /api/agendamentos/cliente/{anaId}` sem `Authorization` | 401 | 09/11/2026 ✅ |
| VAL-02 | Cliente agenda um serviço | `POST /api/agendamentos/cliente/{anaId}` com o token da Ana e o body abaixo | 201 e `"valor": 80.00` | 09/11/2026 ✅ |
| VAL-03 | Estúdio muda o preço do serviço | `PUT /api/empresas/{empresaId}/servicos/{servicoId}` com o token do estúdio e `{"nome":"Corte","duracaoMinutos":60,"preco":120}` | 200 e `"preco": 120` | 09/11/2026 ✅ |
| VAL-04 | Valor antigo preservado na lista da cliente | `GET /api/agendamentos/cliente/{anaId}` com o token da Ana | 200; o agendamento do VAL-02 continua com `"valor": 80.00` | 09/11/2026 ✅ |
| VAL-05 | Valor antigo preservado na lista do estúdio | `GET /api/agendamentos/empresa/{empresaId}` com o token do estúdio | 200; o agendamento do VAL-02 continua com `"valor": 80.00` | 09/11/2026 ✅ |
| VAL-06 | Novo agendamento usa o preço novo | `POST /api/agendamentos/cliente/{anaId}` em outro horário | 201 e `"valor": 120.00` | 09/11/2026 ✅ |
| VAL-07 | Outra cliente não vê os agendamentos da Ana | `GET /api/agendamentos/cliente/{anaId}` com o token da Bia | 403 | 09/11/2026 ✅ |
| VAL-08 | Listar sem token | `GET /api/agendamentos/cliente/{anaId}` sem `Authorization` | 401 | 09/11/2026 ✅ |

Body do VAL-02 (no VAL-06, trocar o horário e a forma de pagamento):

```json
{
  "empresaId": "<empresaId>",
  "servicoId": "<servicoId>",
  "dataHoraInicio": "2026-11-10T10:00:00",
  "formaPagamento": "PIX"
}
```

Observação: agendamentos criados antes desta funcionalidade voltam com `"valor": null`.
O front deve tratar esse caso, por exemplo exibindo o preço atual do serviço ou um traço.

---

## Limite de tentativas de login

O login conta as senhas erradas por IP e por conta (`kira.security.rate-limit.login` no `application.yml`).
Os contadores ficam em memória: reiniciar a API zera todos eles.

| Chave | Regra |
|---|---|
| IP + conta | 5 senhas erradas em 15 min: a 6ª tentativa recebe `429` com `Retry-After` |
| IP | 20 senhas erradas em 15 min, em qualquer conta: `429` com `Retry-After` |
| Conta | a partir de 5 falhas, cada tentativa espera 1 s, 2 s, 4 s, até 8 s. A conta nunca é bloqueada |

Pré-condição: duas clientes cadastradas, A e B, sem nenhuma falha de login nos últimos 15 min.
Os requests usam `POST /api/auth/login` com o body `{"email":"<email>","senha":"<senha>"}`, sem `Authorization`
(a rota é pública).

| ID | Cenário | Request | Esperado | Resultado |
|---|---|---|---|---|
| LIM-01 | Senha errada | login da A com senha errada | 401, `"erro": "CREDENCIAIS_INVALIDAS"`, `"mensagem": "E-mail ou senha inválidos"` | 10/11/2026 ✅ |
| LIM-02 | E-mail sem conta | login com um e-mail que não existe | 401 com a mesma mensagem do LIM-01: a resposta não revela se a conta existe | 10/11/2026 ✅ |
| LIM-03 | 6ª tentativa no mesmo IP e na mesma conta | 5 logins da A com senha errada e, em seguida, um com a senha **certa** | os 5 primeiros: 401. O 6º: 429, `"erro": "TENTATIVAS_EXCEDIDAS"`, header `Retry-After: 900` | 10/11/2026 ✅ |
| LIM-04 | O bloqueio não atinge outra conta | logo depois do LIM-03, login da B com a senha certa | 200 com o token | 10/11/2026 ✅ |
| LIM-05 | Login certo zera as falhas do IP naquela conta | 4 logins da B com senha errada, 1 com a certa e mais 4 com a errada | todos os errados: 401 (nenhum 429), o certo: 200 | 10/11/2026 ✅ |
| LIM-06 | Atraso a partir de 5 falhas na conta | continuação do LIM-05: medir o tempo das respostas | a partir da 5ª falha da conta, as respostas demoram cerca de 1 s, 2 s, 4 s e 8 s. O login certo também espera, mas responde 200 | 10/11/2026 ✅ (1,15 s, 2,15 s, 4,16 s e 8,18 s) |
| LIM-07 | 20 falhas do mesmo IP em contas diferentes | 20 logins errados em contas diferentes, do mesmo IP | a 21ª tentativa recebe 429 | Coberto por `LimiteDeTentativasTest`. Não rodado à mão: bloqueia o login local por 15 min |

Observação: no LIM-05, o login certo zera só o contador **do IP naquela conta**. O contador **da conta** continua,
por isso o LIM-06 já começa a atrasar na 6ª falha total da B.

## Recuperação de senha

O pedido do e-mail de recuperação sempre responde a mesma coisa, exista a conta ou não. O código enviado vale
15 min, é de uso único e fica no banco só como SHA-256 (`kira.security.rate-limit.recuperacao`).

| Chave | Regra |
|---|---|
| E-mail | 3 pedidos por hora: `429` com `Retry-After` |
| IP | 10 pedidos por hora, em qualquer e-mail: `429` com `Retry-After` |
| IP (uso do código) | 10 tentativas de redefinir a cada 15 min: `429` com `Retry-After` |

Rotas públicas, sem `Authorization`:

- `POST /api/auth/recuperar-senha` com `{"email":"<email>"}`
- `POST /api/auth/redefinir-senha` com `{"token":"<código do e-mail>","novaSenha":"<nova senha>"}`

| ID | Cenário | Request | Esperado | Resultado |
|---|---|---|---|---|
| REC-01 | Pedido para conta existente | `recuperar-senha` com o e-mail da A | 200, `"mensagem": "Se o e-mail estiver cadastrado, enviaremos as instruções."` | 10/11/2026 ✅ |
| REC-02 | Pedido para e-mail sem conta | `recuperar-senha` com um e-mail que não existe | 200 com a mesma mensagem e no mesmo tempo do REC-01 | 10/11/2026 ✅ |
| REC-03 | 4º pedido do mesmo e-mail na hora | 4 pedidos seguidos com o e-mail da B | os 3 primeiros: 200. O 4º: 429, `"erro": "TENTATIVAS_EXCEDIDAS"`, header `Retry-After: 3600` | 10/11/2026 ✅ |
| REC-04 | E-mail mal escrito | `recuperar-senha` com `{"email":"nao-e-email"}` | 400, `"erro": "VALIDACAO"`, `"erros": {"email": "Email inválido"}` | 10/11/2026 ✅ |
| REC-05 | Código que não existe | `redefinir-senha` com um código inventado e senha válida | 400, `"mensagem": "Token inválido"` | 10/11/2026 ✅ |
| REC-06 | Senha nova curta | `redefinir-senha` com `"novaSenha": "curta"` | 400, `"erros": {"novaSenha": "Senha deve ter no mínimo 8 caracteres"}` | 10/11/2026 ✅ |
| REC-07 | Redefinir com o código do e-mail | pedir a recuperação com um e-mail real, fazer login antes (guardar o `token` e o `refreshToken`) e usar o código recebido em `redefinir-senha` | 200, sem corpo | |
| REC-08 | Usar o mesmo código de novo | repetir o REC-07 com o mesmo código | 400, `"mensagem": "Token já utilizado"` | |
| REC-09 | JWT de antes da troca deixa de valer | `GET /api/clientes/{clienteId}/perfil` com o `token` guardado no REC-07 | 401 | |
| REC-10 | Refresh token de antes da troca deixa de valer | `POST /api/auth/refresh` com `{"refreshToken":"<guardado no REC-07>"}` | 400, `"mensagem": "Refresh token inválido"` | |
| REC-11 | Senha antiga e senha nova | login com a senha antiga e depois com a nova | antiga: 401. Nova: 200 | |
| REC-12 | 10 pedidos do mesmo IP em e-mails diferentes | 11 pedidos de `recuperar-senha`, cada um com um e-mail diferente | o 11º recebe 429 | Coberto por `LimiteDeTentativasTest`. Não rodado à mão: bloqueia a recuperação local por 1 h |

Os cenários REC-07 a REC-11 dependem do código que chega por e-mail e são feitos à mão, com uma conta de e-mail real.
