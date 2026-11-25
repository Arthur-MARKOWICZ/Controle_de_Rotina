# Proposal

## Why

O app tem deploy automático para produção a cada push na `main` e um cliente Android a caminho, mas o titular não consegue exportar os próprios dados nem excluir a conta. A LGPD (art. 18) exige os dois direitos, e eles são pré-requisito de confiança antes de abrir o app para qualquer usuário além do autor (issue #75, OPP-021).

## What Changes

- **`GET /me/export`**: devolve, como anexo JSON, todos os dados do usuário autenticado — conta, tarefas (com subtarefas), projetos e reservas, tags, hábitos, itens do Decidir, pendências, objetivos e vínculos, capacidade semanal e overrides, planos com blocos, execuções e eventos de XP. Inclui registros com soft-delete/arquivados. Nada de outros usuários.
- **`DELETE /me`** com `senhaAtual` no corpo: apaga a conta e tudo que pertence a ela num único `DELETE`, apoiado em `ON DELETE CASCADE` no banco; revoga o access token atual, apaga os refresh tokens e limpa o cookie de refresh. Responde `204`.
- **Migration V29**: as 14 FKs que apontam para `usuario` e `fk_bloco_plano_plano` passam a `ON DELETE CASCADE`.
- **Sessões de outros dispositivos**: requisição com JWT ainda válido de uma conta excluída recebe `401` com ProblemDetail `title: "Conta excluída"`, em vez de lista vazia ou `500` por violação de FK.
- **Web (Perfil)**: ações "Exportar meus dados" e "Excluir conta" (confirmação forte + senha); tratamento global do 401 "Conta excluída" sem tentar refresh.
- **Android (Perfil)**: as mesmas duas ações e o mesmo tratamento do 401.
- **MCP**: apenas reconhece o 401 "Conta excluída" (erro tipado, sem refresh). O MCP **não** expõe exclusão de conta nem export.

Fora de escopo: exclusão agendada ou com período de arrependimento; export em outros formatos (CSV etc.); export pelo MCP.

## Capabilities

### New Capabilities
- `conta-titular`: direitos do titular sobre a própria conta na API — exportar todos os dados, excluir a conta com confirmação por senha sem deixar linhas órfãs, e o comportamento de sessões e clientes depois da exclusão.
- `ui-conta-titular`: telas de Perfil (web e Android) para exportar dados e excluir a conta, e a reação dos clientes ao 401 "Conta excluída".

### Modified Capabilities
<!-- Nenhuma: as specs existentes não descrevem conta/autenticação. -->

## Impact

- **Backend**: `usuario` (controller `/me`, `UsuarioService`, novo serviço de export e DTOs), `auth` (`JwtAuthenticationFilter`, `RefreshTokenRepository`, `AccessTokenRevogacaoService`), `config/SecurityConfig` (entry point), nova migration `V29`. Repositórios de todos os domínios ganham consultas por `usuario_id` para o export.
- **Contrato**: `contract/openapi.json` ganha `GET /me/export`, `DELETE /me` e os schemas de export → regenerar `schema.d.ts` do front e do MCP; modelos Kotlin do mobile validados contra o contrato.
- **Front web**: `PerfilPage`, `meApi`, `apiClient` (tratamento de 401). Proxy: `/me` já existe, nada a mudar.
- **Mobile**: `RotinaApi`/`ApiHttp`, `PerfilTela`, mecanismo Android para salvar/compartilhar o arquivo exportado (novo `expect/actual`).
- **MCP**: `api-client.ts` (novo `ContaExcluidaError`) e teste que garante ausência de caminho para `DELETE /me`.
- **Riscos**: exclusão é irreversível; export pode ficar pesado em contas grandes; a cascata muda o comportamento de `DELETE` em `usuario` para sempre.
