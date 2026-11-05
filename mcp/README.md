# MCP — controle de rotina

Servidor [Model Context Protocol](https://modelcontextprotocol.io) em stdio que
atua como cliente da API REST. Decisão:
[ADR-0001](../docs/decisions/0001-mcp-cliente-rest-stdio.md).  
Plano: [`plans/negocio/mcp.md`](../plans/negocio/mcp.md).

## Autenticação (estilo mobile)

1. Configure só a URL da API no host MCP.
2. Prefira o CLI local (senha fica no terminal, não no chat do agente):

```bash
cd mcp
CONTROLE_ROTINA_API_BASE_URL=http://localhost:8080 pnpm auth-login
```

   Use `pnpm auth-login` (não `pnpm login` — esse é o login do npm).
3. Alternativa: chame a tool `login` no host MCP (a senha passa pelo contexto
   do agente — evite quando possível).
4. Access + refresh ficam em `~/.config/controle-rotina/mcp-session.json` (ou
   `CONTROLE_ROTINA_SESSION_PATH`), modo `0600`.
5. Em `401`, o cliente renova via `/auth/refresh` e repete a chamada uma vez.
6. Se o refresh falhar, as tools respondem `AUTH_REQUIRED` pedindo
   `pnpm auth-login` de novo.

As tools e o CLI **não** devolvem tokens nem ecoam a senha.

## Tools

| Tool | Ação |
|---|---|
| `login` / `logout` / `auth_status` | Sessão local (`login` = fallback) |
| `get_user_email` | Consulta **somente** o e-mail da conta |
| `list_tarefas` / `get_tarefa` / `create_tarefa` / `update_tarefa` | Tarefas |
| `get_capacidade_resolvida` / `set_capacidade_override` | Capacidade |
| `get_plano` / `gerar_plano` / `add_bloco_plano` / `remove_bloco_plano` / `aprovar_plano` | Plano do dia |
| `list_execucoes` / `iniciar_execucao` / `concluir_execucao` / `abortar_execucao` | Execução |
| `historico_dia` / `historico_semana` | Histórico |

Fora do escopo: mutação/consulta de perfil além do e-mail, soft delete/restore,
projetos, hábitos, calendário, propostas de replanejamento, MFA.

## Configuração

```bash
cd mcp
cp .env.example .env   # ou exporte as variáveis no host MCP
pnpm install
pnpm test
pnpm build
```

Variáveis:

- `CONTROLE_ROTINA_API_BASE_URL` — origem HTTP(S) da API (obrigatória)
- `CONTROLE_ROTINA_SESSION_PATH` — arquivo de sessão (opcional)

## Cursor (`mcp.json`)

```json
{
  "mcpServers": {
    "controle-rotina": {
      "command": "node",
      "args": ["/caminho/absoluto/para/controle_rotina/mcp/dist/index.js"],
      "env": {
        "CONTROLE_ROTINA_API_BASE_URL": "http://localhost:8080"
      }
    }
  }
}
```

Depois de conectar o servidor, rode `pnpm auth-login` no terminal (recomendado)
ou peça ao agente a tool `login`. Em desenvolvimento sem build: `"command": "pnpm"`,
`"args": ["--dir", ".../mcp", "exec", "tsx", "src/index.ts"]`.

## Privacidade

Não registre tokens, Authorization, cookies, senhas, e-mail em falhas de auth
nem títulos de tarefas em logs do host. O processo MCP só escreve banner em
**stderr**.

## Testes

```bash
cd mcp && pnpm test
```
