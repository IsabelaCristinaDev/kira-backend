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
