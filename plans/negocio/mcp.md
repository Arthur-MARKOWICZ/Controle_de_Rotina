# Feature — Servidor MCP (cliente REST stdio)

Escopo: pacote `mcp/` na raiz do monorepo — servidor MCP em TypeScript com
transporte **stdio** que chama a API REST já existente (JWT + refresh cookie),
no mesmo espírito do `controle_gastos` (ADR-023 / pacote `mcp/`, **incluindo**
CLI `auth-login`).  
Permite a um agente (Cursor etc.) operar o núcleo
tarefa → capacidade → plano → execução → histórico **sem UI**, com autenticação
preferencialmente **fora** do chat.

Fora deste slice:

- novos endpoints Spring, mudanças de regra de domínio, OpenAPI novo;
- MFA / OAuth social (API atual não tem — CLI e tools **sem** `verify_mfa`);
- mutação de perfil (`PATCH /me`), cadastro (`POST /usuarios`);
- soft delete / restore de tarefa;
- propostas de replanejamento;
- projetos, hábitos, calendário, XP detalhado (além do que a API já devolve em
  concluir);
- MCP remoto (Streamable HTTP / OAuth do protocolo);
- qualquer mecanismo de cache no cliente MCP.

Dependências: API MVP + pós-MVP de auth/sessão já feitos (`usuario.md`,
`seguranca-jwt-cookie.md`, `seguranca-refresh-token.md`, `tarefa.md`,
`capacidade.md`, `planner.md`, `execucao.md`, `historico.md`).  
Decisão: [`docs/decisions/0001-mcp-cliente-rest-stdio.md`](../../docs/decisions/0001-mcp-cliente-rest-stdio.md).

---

## Conceito

1. O MCP **não** é capacidade de negócio: é um cliente a mais da API.
2. Transporte **stdio**; host configura só a URL base da API.
3. Sessão local (access + refresh + nome do cookie) em arquivo `0600`; nem tools
   nem CLI devolvem tokens nem ecoam senha.
4. **Auth preferida:** CLI `pnpm auth-login` (bin `controle-rotina-login`) lê
   e-mail/senha no terminal (senha oculta via bash `read -s`) e grava a sessão.
   Tool `login` é **fallback** (senha no contexto do agente — evitar).
5. Em `401` em chamada autenticada: `POST /auth/refresh` com Cookie
   `refresh_token` (path da API = `/auth`) → **uma** retentativa; se falhar →
   limpar sessão → `AUTH_REQUIRED` apontando **primeiro** para o CLI.
6. Tools devolvem JSON textual; erros da API viram `{ status, body }` (ProblemDetail
   quando a API já responde assim).
7. Privacidade: `get_user_email` lê `GET /me` e **só** devolve e-mail.

---

## Entity / Entities

Nenhuma entity nova. Reuso integral do modelo e dos contratos HTTP atuais.

---

## Back-end (pacote `mcp/`)

Sem controllers Spring novos. Prefixo HTTP novo: **não** (não altera proxy Vite/Nginx).

### Layout do pacote (espelhar gastos)

| Artefato | Papel |
|----------|--------|
| `mcp/package.json` | Node ≥ 24, pnpm; bins `controle-rotina-mcp` + `controle-rotina-login`; scripts `build` / `test` / `dev` / **`auth-login`** |
| `mcp/src/config.ts` | `CONTROLE_ROTINA_API_BASE_URL` (obrigatória), `CONTROLE_ROTINA_SESSION_PATH` (opcional) |
| `mcp/src/session-store.ts` | load/save/clear; default `~/.config/controle-rotina/mcp-session.json`; modo `0600` |
| `mcp/src/cookies.ts` | parse de `Set-Cookie` → nome + valor do refresh |
| `mcp/src/api-client.ts` | HTTP mínimo: login, refresh, logout, request autenticado + retry 401 |
| `mcp/src/terminal-prompt.ts` | `askVisible` (readline) + `readSecretWithBash` (senha oculta) |
| `mcp/src/login-flow.ts` | fluxo interativo testável (`AskFn`); **sem** ramo MFA |
| `mcp/src/login-cli.ts` | entrypoint CLI; usa FileSessionStore + ApiClient + login-flow |
| `mcp/src/server.ts` | registro das tools MCP + lista `MCP_TOOL_NAMES` |
| `mcp/src/index.ts` | bootstrap stdio; banner só em **stderr** |
| `mcp/README.md` | setup, env, **auth-login primeiro**, exemplo `mcp.json` Cursor |
| `mcp/.env.example` | só URL (sem segredos) |

Dependências: `@modelcontextprotocol/sdk`, `zod` (schemas de input das tools).  
Testes: Vitest (unitários com `fetch` mock / session em memória / `AskFn` fake) — **sem** Testcontainers.

Script `auth-login`: `tsx src/login-cli.ts` (não usar o nome `pnpm login` — conflita com o registry npm).

### Auth HTTP (contrato real da API)

| Chamada | Detalhe |
|---------|---------|
| `POST /auth/login` | body `{ email, password }` → `{ accessToken }` + `Set-Cookie: refresh_token=…; Path=/auth; …` (**sem** `expiresIn` no body — diferente do gastos) |
| `POST /auth/refresh` | Cookie `refresh_token` → novo access (+ rotação de refresh via Set-Cookie) |
| `POST /auth/logout` | Cookie refresh + Bearer opcional → 204 + cookie limpo |
| `GET /me` | Bearer → nome/email; MCP **projeta** só `{ email }` |

Sem tool/CLI `verify_mfa`. Login inválido: 401 ProblemDetail — tool propaga status/body; CLI imprime falha genérica com status, **sem** ecoar senha.

### CLI de autenticação (padrão)

```bash
cd mcp
CONTROLE_ROTINA_API_BASE_URL=https://SUA_API pnpm auth-login
```

1. Pede e-mail (visível) e senha (oculta).
2. Chama o mesmo `ApiClient.login` das tools.
3. Persiste sessão no path configurado.
4. Mensagem de sucesso **sem** tokens/senha (ex.: “Sessão salva. Pode voltar ao chat.”). Como a API não manda `expiresIn`, não inventar TTL no JSON — opcional mencionar “access ~15 min” na copy fixa.

### Tools v1

| Tool | API | Observação |
|------|-----|------------|
| `login` | `POST /auth/login` | **Fallback**; preferir CLI. Resposta `{ authenticated: true }` (sem token) |
| `logout` | `POST /auth/logout` | Best-effort na API + `clear` local |
| `auth_status` | — | `{ hasSession: boolean }` sem tokens |
| `get_user_email` | `GET /me` | Só e-mail |
| `list_tarefas` | `GET /tarefas` | Query: `status`, `prioridade`, `page`, `size` (defaults da API); **não** `incluirDeletadas`/`somenteDeletadas` |
| `get_tarefa` | `GET /tarefas/{id}` | 404 de outro usuário / inexistente |
| `create_tarefa` | `POST /tarefas` | Campos do `CriarTarefaRequest` atual |
| `update_tarefa` | `PATCH /tarefas/{id}` | Campos do `AtualizarTarefaRequest` atual |
| `get_capacidade_resolvida` | `GET /capacidades/resolvida` | `dataDe` / `dataAte` opcionais |
| `set_capacidade_override` | `POST /capacidades/overrides` | body alinhado a `CriarCapacidadeOverrideRequest` |
| `get_plano` | `GET /planos?data=` | `versao` opcional; 404 se não houver |
| `gerar_plano` | `POST /planos/gerar?data=` | Nova versão rascunho |
| `add_bloco_plano` | `POST /planos/{id}/blocos` | Ajuste rascunho |
| `remove_bloco_plano` | `DELETE /planos/{id}/blocos/{blocoId}` | Ajuste rascunho |
| `aprovar_plano` | `POST /planos/{id}/aprovar` | |
| `list_execucoes` | `GET /execucoes?data=` | Default hoje (API) |
| `iniciar_execucao` | `POST /execucoes/iniciar` | body `{ blocoId }` |
| `concluir_execucao` | `POST /execucoes/{id}/concluir` | Devolve o que a API já manda (incl. XP se vier) |
| `abortar_execucao` | `POST /execucoes/{id}/abortar` | |
| `historico_dia` | `GET /historico/dia` | `data` opcional |
| `historico_semana` | `GET /historico/semana` | `semanaInicio` opcional |

`AUTH_REQUIRED.action` (texto sugerido): pedir `cd mcp && pnpm auth-login` (ou bin); tool `login` só como alternativa.

### Regras do cliente

1. Base URL sem path forçado tipo `/api/v1` (rotina não usa esse prefixo).
2. Nunca logar Authorization, Cookie, senha, access/refresh, e-mail em falhas de auth.
3. `MCP_TOOL_NAMES` exportado e coberto por teste de superfície (lista fechada = ADR).
4. Sem cache de respostas HTTP.
5. CLI e tools compartilham `ApiClient` + `SessionStore` (uma sessão, dois entrypoints).

---

## Front-end

Não há tela no `frontend-web`. Entregável de “cliente”:

1. `mcp/README.md` com fluxo **auth-login primeiro**, depois bloco Cursor `mcp.json`
   (`command`/`args`/`env` — **sem** senha no env).
2. Menção opcional no README raiz do monorepo (uma linha + link ao ADR) — sem redesign.
3. Sem alteração de `vite.config.ts` / `nginx.conf` / `API_PROXY.md`.

---

## Decisões fechadas (revisão)

1. Estratégia idêntica ao controle de gastos: MCP TypeScript **stdio** como cliente REST; Spring continua única fonte de regras.
2. Sessão local estilo mobile (access + refresh em arquivo `0600`); rejeitado token só no `mcp.json`.
3. **Auth padrão = CLI `pnpm auth-login`**; tool `login` é fallback (espelho do README/CLI novos do gastos).
4. Sem MFA (nem tool nem ramo no `login-flow`); sem MCP embutido no Spring; MCP remoto adiado (ADR-0001).
5. Privacidade: só e-mail do `/me`; sem mutar perfil; sem cadastro via MCP; CLI/tools nunca ecoam senha.
6. Corte v1 = núcleo planejamento/execução + histórico leitura (tabela de tools acima); hábitos/projetos/calendário/soft-delete fora.
7. Cookie `refresh_token` com `Path=/auth` — cliente envia Cookie só nas rotas `/auth/*` de refresh/logout.
8. Login da API rotina **sem** `expiresIn` no body — CLI não depende desse campo.
9. Sem endpoints novos e sem cache.
10. Testes do pacote em Vitest; regressão de superfície via `MCP_TOOL_NAMES` + testes do `login-flow` / `terminal-prompt`.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*) que o entrega.

### Unitários (sem HTTP real)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `config`: URL obrigatória; session path default vs env | B1 | Bootstrap quebrado = MCP inútil |
| T2 | `session-store`: save/load/clear; arquivo com modo `0600` (best-effort) | B2 | Segredo de máquina |
| T3 | `cookies`: extrai `refresh_token` de `Set-Cookie` (vários headers / atributos) | B2 | Refresh depende disso |
| T4 | `api-client` login: persiste access+refresh; resposta **sem** tokens ao caller das tools | B3 | Privacidade da sessão |
| T5 | `api-client`: em 401, chama refresh **uma** vez e repete; refresh falho → clear + `AuthRequiredError` | B3 | Access 15 min |
| T6 | `api-client` logout: clear local mesmo se API falhar (best-effort documentado) | B3 | Não deixar sessão zumbi só local vs só remota |
| T7 | `login-flow`: autentica com `AskFn` fake; **não** imprime a senha nas linhas de saída; e-mail/senha vazios não chamam API | B4 | Caminho padrão de auth |
| T8 | `terminal-prompt`: `readSecretWithBash` devolve senha sem `\n` final; falha se bash status ≠ 0 | B4 | Senha oculta no terminal |
| T9 | Superfície: `MCP_TOOL_NAMES` == lista do ADR (sorted); nomes proibidos (`verify_mfa`, tools de projeto/hábito/calendário/delete tarefa) **ausentes** | B5 | Regressão de escopo |
| T10 | Tools auth: `login`/`logout`/`auth_status`/`get_user_email` — happy path + `AUTH_REQUIRED` sem sessão (action menciona CLI) | B5 | Porta de entrada + copy do fallback |
| T11 | Tools domínio (amostra): create/list tarefa, get/aprovar plano, iniciar execução — mapeiam path/método/body corretos (fetch mock) | B6–B9 | Contrato HTTP |
| T12 | Erro API → `isError` com `{ status, body }` parseado quando JSON | B5 | ProblemDetail legível no host |

### Integração / API (Spring)

Nenhum IT Spring obrigatório neste slice (zero mudança Java). Opcional manual: smoke com API local + `pnpm auth-login` + server stdio.

### Front

Harness front do app: N/A. Aceite manual: `pnpm auth-login` → conectar `mcp.json` no Cursor → tool de domínio sem nova senha no chat.

---

## Atividades

### Backend (pacote MCP)

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Scaffold `mcp/`** | `package.json` (bins mcp + login, script `auth-login`), tsconfig, vitest, `config.ts` + **T1**; `index` stdio mínimo (banner stderr); `.env.example` |
| **B2** | **Sessão + cookies** | `session-store` (file + memory), `cookies` parse Set-Cookie; **T2**, **T3** |
| **B3** | **ApiClient auth** | login / refresh-on-401 / logout; sem log de segredos; **T4**, **T5**, **T6** |
| **B4** | **CLI auth** | `terminal-prompt`, `login-flow` (sem MFA), `login-cli`; **T7**, **T8** |
| **B5** | **Tools auth + e-mail + erros** | `login` (fallback), `logout`, `auth_status`, `get_user_email`; `AUTH_REQUIRED` → CLI; `MCP_TOOL_NAMES` inicial; **T9**, **T10**, **T12** |
| **B6** | **Tools tarefas** | `list_tarefas`, `get_tarefa`, `create_tarefa`, `update_tarefa`; **T11** (parte tarefas) |
| **B7** | **Tools capacidade** | `get_capacidade_resolvida`, `set_capacidade_override` |
| **B8** | **Tools plano** | `get_plano`, `gerar_plano`, `add_bloco_plano`, `remove_bloco_plano`, `aprovar_plano`; **T11** (parte plano) |
| **B9** | **Tools execução + histórico** | `list_execucoes`, `iniciar_execucao`, `concluir_execucao`, `abortar_execucao`, `historico_dia`, `historico_semana`; **T11** (parte execução); fechar lista `MCP_TOOL_NAMES` = ADR |

### Frontend / docs host

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **README MCP + ponteiro** | `mcp/README.md`: `auth-login` como passo 1 (avisar contra `pnpm login`); `mcp.json` sem senha; link ADR; linha no README raiz se existir; script `test` no CI/lefthook **somente se** o monorepo já tiver gancho semelhante (senão documentar `cd mcp && pnpm test`) |

### Ordem

```text
B1 → B2 → B3 → B4 → B5 → B6 → B7 → B8 → B9 → F1
```

F1 pode rascunhar após B1 e fechar depois de B9 (lista final de tools + CLI).
