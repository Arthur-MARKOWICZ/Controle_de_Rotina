# ADR-0001: Servidor MCP como cliente REST via stdio

## Status

Aceito (planejado neste slice)

## Data

2026-11-05

## Contexto

Agentes de IA (Cursor e outros hosts MCP) precisam consultar a rotina, criar
tarefas, revisar/aprovar o plano do dia e executar blocos sem passar pela UI.
A API REST autenticada por JWT já é o contrato público entre clientes e o
monólito Spring. Expor um segundo canal de mutação dentro do processo Spring
(módulo novo, filas ou cache) aumentaria superfície e conflitaria com a
arquitetura atual sem ganho claro.

Há restrição de privacidade alinhada ao controle de gastos: o MCP não altera
perfil/usuário nem expõe do `/me` mais do que o e-mail. Cadastro de conta,
soft delete/restore de tarefas, propostas de replanejamento, projetos, hábitos
e calendário ficam fora do corte v1.

O access JWT dura 15 minutos (`app.security.jwt.access-token-ttl`). Colar o
token no `mcp.json` obriga renovação manual constante. O front já resolve isso
guardando o refresh e reenviando-o como cookie em `POST /auth/refresh` (cookie
`refresh_token`, path `/auth`).

Colar a **senha** na tool `login` pelo chat do agente também é risco: a senha
entra no contexto do host. O controle de gastos passou a preferir um CLI local
(`pnpm auth-login`) que lê e-mail/senha no terminal e grava a mesma sessão em
disco; a tool `login` fica como fallback.

Referência de estratégia: `controle_gastos` ADR-023 + pacote `mcp/` (inclui
`login-cli` / `login-flow` / `terminal-prompt`).

## Decisão

- Adicionar o pacote `mcp/` na raiz do monorepo: servidor MCP em TypeScript,
  transporte **stdio**, que chama a API nos paths já existentes (`/auth`,
  `/tarefas`, `/planos`, …) com `Authorization: Bearer`.
- Autenticar no estilo sessão local (como o MCP de gastos / mobile):
  - `CONTROLE_ROTINA_API_BASE_URL` no ambiente do processo;
  - **preferir** CLI `pnpm auth-login` (bin `controle-rotina-login`) que persiste
    a sessão sem passar a senha pelo chat do agente;
  - tools `login` / `logout` / `auth_status` (sem MFA — a API atual não tem);
    `login` é fallback quando o CLI não for praticável;
  - persistir access + refresh + nome do cookie em arquivo local
    (`CONTROLE_ROTINA_SESSION_PATH` ou
    `~/.config/controle-rotina/mcp-session.json`), modo `0600`;
  - em `401`, renovar via `POST /auth/refresh` (Cookie `refresh_token=…`) e
    repetir a chamada **uma** vez; se falhar, limpar a sessão e pedir autenticação
    de novo (mensagem `AUTH_REQUIRED` aponta primeiro para o CLI).
- Não devolver tokens nem senha no conteúdo das tools nem no CLI; não registrar
  tokens, cookies, senhas ou corpos de auth em logs (só banner em stderr).
- Manter a API Spring como única fonte de regras e autorização por recurso.
- Escopo das tools neste ADR (v1):
  - autenticar sessão MCP (`login`, `logout`, `auth_status`) + CLI `auth-login`;
  - consultar somente o e-mail (`GET /me`, resposta minimizada);
  - listar/detalhar, criar e atualizar tarefas (`GET/POST/PATCH /tarefas`);
  - consultar capacidade resolvida e criar override do dia
    (`GET /capacidades/resolvida`, `POST /capacidades/overrides`);
  - consultar plano do dia, gerar rascunho, ajustar blocos e aprovar
    (`GET/POST /planos…`);
  - listar execuções do dia e iniciar/concluir/abortar
    (`GET/POST /execucoes…`);
  - resumo de histórico dia/semana (`GET /historico/dia`, `/historico/semana`).
- Proibido neste servidor v1: mutar perfil/usuário, expor `id`/nome/datas da
  conta, soft delete/restore de tarefa, propostas de replanejamento, projetos,
  hábitos, calendário, template semanal completo, cadastro (`POST /usuarios`).

## Alternativas consideradas

### Access token só no ambiente (`mcp.json`)

- Vantagem: zero persistência local de refresh.
- Desvantagem: renovação manual a cada ~15 min.
- Rejeitado para uso cotidiano; substituído pela sessão local.

### Só tool `login` (senha no chat do agente)

- Vantagem: zero CLI.
- Desvantagem: senha no contexto do host MCP.
- Rejeitado como caminho **padrão**; permanece só como fallback. O padrão é o
  CLI de terminal (espelho do controle de gastos).

### Módulo Spring embutido (MCP HTTP no mesmo processo)

- Vantagem: um único deploy.
- Desvantagens: novo contrato no processo da API, dependências MCP no backend
  Java e risco de acoplar o host de agentes ao ciclo de vida da API.
- Rejeitado: o MCP é um cliente a mais, não uma capacidade de negócio.

### MCP remoto Streamable HTTP com OAuth do protocolo

- Vantagem: UX nativa de conexão no host e multi-cliente.
- Desvantagens: expor MCP na VPS, novo modelo de auth e operação.
- Adiado: permanece alternativa futura; o corte atual prioriza stdio local.

## Consequências

- A API REST permanece a fonte de verdade; o MCP só envelopa.
- A sessão local é segredo de máquina: permissões restritas e fora do Git.
- Fluxo recomendado: `pnpm auth-login` no terminal → host MCP usa a sessão já
  gravada; tool `login` só se necessário e nunca ecoa a senha.
- A API de rotina devolve `{ accessToken }` no login (sem `expiresIn` no body);
  o CLI confirma sucesso sem depender de TTL no JSON (TTL conhecido: ~15 min).
- Ampliar tools (hábitos, projetos, calendário, soft delete) exige atualizar
  este ADR e o plano `plans/negocio/mcp.md`.
- Cookie de refresh com `Path=/auth`: o cliente MCP deve enviar o Cookie
  explicitamente em `/auth/refresh` e `/auth/logout` (não depende do jar do
  browser).
