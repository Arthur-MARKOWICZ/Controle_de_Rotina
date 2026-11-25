# Design

## Context

Motivação em `proposal.md`; comportamento em `specs/conta-titular` e `specs/ui-conta-titular`.

Estado atual relevante:

- **Banco**: 14 FKs apontam para `usuario (id)`, todas sem `ON DELETE` (= `NO ACTION`). Dentro do grafo do usuário há FKs `NO ACTION` também: `bloco_plano → plano/tarefa/habito`, `execucao → bloco_plano`, `tarefa.parent_id`, `tarefa.projeto_id`, `reserva_projeto_semanal → projeto`. Tabelas de junção (`*_tag`, `objetivo_*`, `habito_dia_semana`) já têm `ON DELETE CASCADE`. `bloco_plano` é a única tabela do grafo sem `usuario_id`.
- **Auth**: access token JWT sem estado, TTL 15 min. `JwtAuthenticationFilter` valida assinatura e consulta `AccessTokenRevogacaoService` (revogação por `jti`, cache `simple` em memória). Não consulta o banco. O 401 padrão sai do `authenticationEntryPoint` em `SecurityConfig` com `title` "Não autorizado".
- **Refresh**: token opaco com hash em `refresh_token`; token inexistente → `CredenciaisInvalidasException` (401).
- **Senha atual**: `UsuarioService.atualizarMe` já valida `senhaAtual` com `PasswordEncoder` e lança `CredenciaisInvalidasException`.
- **Clientes**: web (`apiClient.ts`), Android (`ApiHttp.kt`) e MCP (`api-client.ts`) tentam refresh em qualquer 401. O módulo `shared` do mobile tem só `commonMain` + `jvm()`; serviços de plataforma são interfaces implementadas no `androidApp` e injetadas por `AppDependencias`.
- **Deploy**: um push na `main` faz deploy em produção; o processo reinicia com frequência.

## Goals / Non-Goals

**Goals:**
- Exclusão garantida pelo banco, não pela memória de quem escreve o service.
- Nenhum `500` para quem ainda tem token de uma conta excluída.
- Export tipado no contrato OpenAPI, para os clientes gerarem tipos.

**Non-Goals:**
- Streaming ou paginação do export (escala atual não exige).
- Rate limit específico para `DELETE /me` (exige token válido, mesmo nível do `PUT /me` atual).
- Distinguir clientes (web/Android/MCP) no backend.

## Decisions

### D1. Exclusão via `ON DELETE CASCADE` + um único `DELETE`
Migration `V29__usuario_on_delete_cascade.sql`: recria com `ON DELETE CASCADE` as 14 FKs para `usuario` e `fk_bloco_plano_plano`. O service executa `DELETE FROM usuario WHERE id = ?` (JPQL `@Modifying` em `UsuarioRepository`, sem carregar entidades).

As FKs internas (`bloco_plano → tarefa/habito`, `execucao → bloco_plano`, `tarefa.parent_id`, `tarefa.projeto_id`, `reserva_projeto_semanal → projeto`) continuam `NO ACTION`, mas passam a `DEFERRABLE INITIALLY IMMEDIATE`. `UsuarioRepository.excluirPorId` roda `SET CONSTRAINTS ALL DEFERRED` antes do `DELETE`, então essas checagens vão para o commit, quando as duas pontas já sumiram. Fora dessa transação nada muda: um `DELETE` isolado de tarefa, hábito, projeto ou bloco continua barrado na hora.

**Hipótese descartada (verificada no apply):** a ideia inicial era deixar as FKs internas como `NO ACTION` puro, supondo que o Postgres só as checa no fim do comando. O `ExclusaoContaIT` mostrou o contrário: dentro da cascata, `fk_bloco_plano_tarefa` é checada a cada passo e a exclusão falha.

- *Alternativa descartada*: `CASCADE` nas FKs internas. `PropostaService` apaga `bloco_plano` diretamente; com cascata em `execucao → bloco_plano`, as execuções (e o histórico de XP derivado) sumiriam em silêncio em vez de a operação falhar como hoje.
- *Alternativa*: deletes ordenados no service, repositório por repositório → frágil; toda tabela nova precisa lembrar de entrar na lista.
- *Alternativa*: `cascade = REMOVE` no JPA → as entidades não mapeiam essas relações; carregaria tudo em memória.
- *Salvaguarda*: o `ExclusaoContaIT` prova zero órfãs e que deletes isolados continuam barrados.
- *Convenção futura*: toda tabela nova com `usuario_id` declara `ON DELETE CASCADE`; o IT de zero órfãs consulta `information_schema` e falha se encontrar FK para `usuario` sem cascata.

### D2. `DELETE /me` com corpo `{ senhaAtual }`
Novo record `ExcluirContaRequest(@NotBlank String senhaAtual)`. `UsuarioService.excluirConta(usuarioId, request)` numa transação: valida senha (reusa o padrão de `atualizarMe`) → `DELETE`. Depois do commit, o controller revoga o `jti` atual com `AuthService.revogarAccessToken` (extraído do `logout`); assim uma falha no commit não deixa o usuário deslogado com a conta ainda existindo. O controller responde `204` com `Set-Cookie` de `RefreshCookieFactory.limpar()`. Refresh tokens somem pela cascata; `/auth/refresh` passa a devolver o 401 de credenciais inválidas já existente.

- *Alternativa*: `POST /me/exclusao` → evita corpo em DELETE, mas a issue define `DELETE /me` e fetch/Ktor/nginx aceitam corpo.

### D3. Detecção de conta excluída no filtro JWT, consultando o banco
Depois de validar assinatura e `jti`, o `JwtAuthenticationFilter` chama `UsuarioRepository.existsById(sub)`. Se não existir, não autentica e marca o request (`request.setAttribute("contaExcluida", true)`); o `authenticationEntryPoint` lê o atributo e escreve o ProblemDetail com `title` "Conta excluída" e `detail` "Esta conta foi excluída.". Rotas `permitAll` não são afetadas.

Um JWT assinado por nós só existe para um usuário que já existiu, então "sub inexistente" = "conta excluída", sem tabela de lápide.

- *Alternativa*: cache em memória de usuários excluídos (TTL = access TTL) → sem custo de banco, mas perde-se em cada deploy/restart, reabrindo a janela de `500` por FK.
- *Custo*: uma busca por PK por requisição autenticada; desprezível na escala atual. Se virar gargalo, um cache positivo curto (ex.: 30 s) entra sem mudar a spec.

### D4. Export tipado, montado por consultas por usuário
Novo `ExportService` (pacote `usuario`) em `@Transactional(readOnly = true)` monta `ExportResponse` com uma lista por domínio. Cada domínio tem um record próprio de export (num único arquivo `ExportResponse.java` com records aninhados), desacoplado dos DTOs de tela. O serviço faz uma consulta JPQL por entidade (`where e.usuarioId = :u`, sem filtro de soft-delete/arquivado); `bloco_plano` vem por `planoId in`, e vínculos de tag/objetivo por `tagId`/`objetivoId in`. Nenhum repositório ganha método só para o export. Campos de ligação (ids, `parentId`, `projetoId`, `planoId`) vão como ids, não aninhados, para o JSON refletir o modelo sem duplicação.

Controller: `GET /me/export` devolve `ResponseEntity<ExportResponse>` com `Content-Disposition: attachment; filename="controle-rotina-export-AAAA-MM-DD.json"` (data em BRT pelo `Clock`). Campo `geradoEm` e `versao: 1` no topo para permitir evolução do formato.

- *Alternativa*: `SELECT *` genérico por tabela em `Map` → zero manutenção, mas o contrato vira `object` livre e vaza nomes de coluna.
- *Alternativa*: reusar DTOs de tela → mudam por razões de UI e omitem soft-deleted.

### D5. Clientes: checar o `title` antes do refresh
Web, Android e MCP leem o corpo do 401 (tolerando corpo vazio/não-JSON) e, se `title == "Conta excluída"`, pulam o refresh, limpam a sessão e sinalizam o motivo:
- **Web**: `apiFetch` lê o ProblemDetail do 401 com `response.clone()`; em "Conta excluída" chama `encerrarSessaoContaExcluida()` (limpa o token e grava um aviso de uma vez em `sessionStorage`) e devolve a resposta sem refresh. O `ProtectedRoute` existente leva ao login, que mostra o aviso. Não há erro novo: os chamadores continuam tratando `!response.ok` como antes. O 401 de senha errada (`detail` "Credenciais inválidas.") também não dispara refresh. `meApi` ganha `exportarDados()` (blob + nome do `Content-Disposition`) e `excluirConta(senhaAtual)`.
- **Android**: `ApiHttp` lança `ApiErro.ContaExcluida`, apaga a sessão local e emite `Autenticador.contaExcluida`; o `SessaoViewModel` escuta esse flow (como já faz com `sessaoExpirada`) e volta ao login com "Sua conta foi excluída.". `RotinaApi` ganha `exportarDados(): ByteArray` e `excluirConta(senhaAtual)`; um `PerfilViewModel` concentra o estado das duas ações. Nova interface `SalvadorArquivo` em `commonMain`, implementada no `androidApp` com `ActivityResultContracts.CreateDocument("application/json")` (SAF, sem `FileProvider`).
- **MCP**: `api-client.ts` lança `ContaExcluidaError` e limpa o `SessionStore`. Nenhum método para `DELETE /me` ou `/me/export`; um teste varre ferramentas registradas e o client para garantir isso.

O texto "Conta excluída" é constante no backend (`JwtAuthenticationFilter.TITULO_CONTA_EXCLUIDA`) e em cada cliente (`TITULO_CONTA_EXCLUIDA` no web, no Android e no MCP). O contrato OpenAPI não descreve os 401, então a guarda é por teste: o `ContaExcluidaIT` fixa o literal no backend e os testes de cada cliente usam a constante contra respostas com esse título.

### D6. Confirmação forte na UI
Diálogo com texto de irreversibilidade, link para exportar antes, campo de senha e campo de confirmação que exige `EXCLUIR`. Erro 401 de senha fica no campo, sem acionar o fluxo de refresh (a chamada de exclusão trata 401 "Credenciais inválidas" como erro de formulário).

## Risks / Trade-offs

- [Exclusão irreversível por engano] → senha + `EXCLUIR` + sugestão de export antes; nenhum endpoint de exclusão no MCP.
- [Cascata torna `DELETE FROM usuario` destrutivo para sempre, inclusive manual] → é exatamente o comportamento desejado; documentado na migration.
- [`SET CONSTRAINTS ALL DEFERRED` afeta toda constraint adiável da transação] → a transação de exclusão só faz o `DELETE`; as únicas constraints adiáveis são as seis FKs internas da V29.
- [Requisição concorrente inserindo dados durante a exclusão] → a FK trava a linha de `usuario`; a inserção espera e falha com violação de FK depois do commit (ProblemDetail "Erro de integridade" do handler atual). Requisições seguintes já recebem "Conta excluída".
- [Consulta extra por requisição autenticada] → busca por PK; cache curto possível sem mudar specs.
- [Export grande em contas grandes] → uma transação read-only, sem N+1 (consultas `In` por domínio); streaming fica para quando houver contas que justifiquem.
- [Cache de revogação por `jti` é em memória] → irrelevante aqui: D3 cobre o caso mesmo após restart.

## Migration Plan

1. Deploy do backend com V29 (Flyway). A migration só troca constraints; sem mudança de dados. Em tabelas pequenas, roda em segundos.
2. Os clientes antigos continuam funcionando: os endpoints são novos e o 401 "Conta excluída" só aparece para contas excluídas (que ainda não existem antes do deploy).
3. Rollback: reverter o código é seguro; a V29 pode permanecer (cascata não muda nenhum fluxo existente, pois nada apaga `usuario` hoje).
