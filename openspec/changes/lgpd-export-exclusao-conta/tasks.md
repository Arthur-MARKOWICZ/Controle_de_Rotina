# Tasks

Cada unidade com lógica segue o 1+3+3 (feliz + 3 anomalias + 3 erros com `status` + `$.title`). Tempo sempre por `Clock` fixo. Mocks do Vitest com `vi.mocked(...)`.

## 1. Banco: cascata a partir de `usuario`

- [x] 1.1 Criar `backend/src/main/resources/db/migration/V29__usuario_on_delete_cascade.sql` recriando com `ON DELETE CASCADE` as 14 FKs para `usuario (id)` e `fk_bloco_plano_plano`, e tornando as FKs internas `DEFERRABLE INITIALLY IMMEDIATE` (design D1). Verificar com `cd backend && mvn test -Dtest='*IT'` subindo o Flyway sem erro.
- [x] 1.2 Adicionar em `UsuarioRepository` o `@Modifying` JPQL `excluirPorId(UUID)`. Criar `ExclusaoContaIT` (Testcontainers) que monta dados de dois usuários em **todos** os domínios — tarefa com subtarefa, projeto com reserva, tags vinculadas a tarefa/hábito/projeto/Decidir, hábito com dias, pendência, objetivo com vínculos, capacidade + override, plano com bloco e execução concluída, evento de XP, refresh token — chama `excluirPorId(A)` e verifica:
  - feliz: zero linhas de A em cada tabela (consulta por `usuario_id` e, para `bloco_plano` e junções, pelos ids de A);
  - anomalias: contagens de B inalteradas; usuário sem dados nenhum é excluído; e-mail de A pode ser recadastrado;
  - convenção: consulta em `information_schema` falha se houver FK para `usuario` sem `ON DELETE CASCADE`.
  Também: deletes isolados de tarefa, projeto e bloco com execução continuam barrados.

## 2. Backend: exclusão de conta

- [x] 2.1 Criar `ExcluirContaRequest(@NotBlank String senhaAtual)` e `UsuarioService.excluirConta(usuarioId, request)` (transacional: valida senha → `excluirPorId`). O `jti` atual é revogado pelo controller via `AuthService.revogarAccessToken` só depois do commit. Verificar em `UsuarioServiceTest`: feliz; anomalias (usuário inexistente, token sem `jti`, exceção no delete não revoga); erros (`CredenciaisInvalidasException` para senha errada, nada é apagado; senha nula).
- [x] 2.2 Adicionar `@DeleteMapping` em `MeController` respondendo `204` com `Set-Cookie` de `RefreshCookieFactory.limpar()`. Verificar em `ExcluirContaIT`:
  - feliz: `204`, cookie expirado, dados apagados;
  - anomalias: segundo `DELETE /me` com o mesmo token → `401`; `/auth/refresh` depois → `401` sem emitir token; falha simulada no meio mantém tudo (atomicidade);
  - erros: senha errada → `401` `$.title` "Não autorizado" e `$.detail` "Credenciais inválidas." com dados intactos; sem `senhaAtual` → `400` `$.title` "Dados inválidos" com `errors[].property` `senhaAtual`; JSON malformado → `400` `$.title` "Requisição inválida"; sem token → `401`.

## 3. Backend: 401 "Conta excluída"

- [x] 3.1 No `JwtAuthenticationFilter`, após validar assinatura e `jti`, checar `UsuarioRepository.existsById(sub)`; se ausente, não autenticar e marcar `request.setAttribute("contaExcluida", true)`. No `authenticationEntryPoint` de `SecurityConfig`, escrever `title` "Conta excluída" / `detail` "Esta conta foi excluída." quando o atributo existir (design D3). Verificar em `ContaExcluidaIT`:
  - feliz: usuário existente continua autenticando normalmente;
  - anomalias: token de outro "dispositivo" (outro `jti`) após exclusão → `401` "Conta excluída" em `GET /tarefas`; `POST /tarefas` → `401` "Conta excluída" e nenhuma linha inserida; rota `permitAll` (`POST /auth/login`) não consulta usuário;
  - erros: token expirado → `401` "Não autorizado" (não "Conta excluída"); token revogado por logout → `401` "Não autorizado"; token com assinatura inválida → `401` "Não autorizado".
  Adicionar casos de unidade no teste do filtro para completar o 1+3+3.

## 4. Backend: export

- [x] 4.1 Consultas por usuário sem filtro de soft-delete/arquivado: uma consulta JPQL por entidade no próprio `ExportService` (`usuarioId`; `bloco_plano` por `planoId in`; vínculos por `tagId`/`objetivoId in`), sem novos métodos de repositório. Coberto pelos testes de 4.2 e 4.3.
- [x] 4.2 Criar `ExportResponse` (records aninhados por domínio, `versao: 1`, `geradoEm`, conta sem `senhaHash`) e `ExportService` read-only (design D4). Verificar em `ExportServiceTest`: feliz (todos os domínios); anomalias (conta vazia → listas vazias + capacidade padrão; tarefa soft-deleted com `deletedAt`; objetivo arquivado e concluído presentes; hábito excluído presente); erros (usuário inexistente → `CredenciaisInvalidasException`, como o `buscarPorId` atual; falha de repositório propaga sem resposta parcial; `usuarioId` nulo → `NullPointerException`/`IllegalArgumentException` antes de consultar).
- [x] 4.3 Adicionar `GET /me/export` em `MeController` com `Content-Disposition: attachment; filename="controle-rotina-export-AAAA-MM-DD.json"` (data BRT pelo `Clock`). Verificar em `ExportIT`:
  - feliz: `200`, header de anexo, uma seção por domínio com os dados de A;
  - anomalias: nenhum id de B no corpo; nome do arquivo na virada de dia BRT (Clock 02:59Z → dia anterior); corpo sem `senhaHash` nem refresh tokens;
  - erros: sem token → `401` "Não autorizado"; token de conta excluída → `401` "Conta excluída"; token expirado → `401` "Não autorizado".

## 5. Contrato OpenAPI

- [x] 5.1 Anotar campos anuláveis de `ExportResponse` com `@Nullable`, regenerar com `cd backend && mvn test -Dtest=ContratoOpenApiIT -Dcontrato.atualizar=true`, depois `cd frontend/frontend-web && npm run gerar:contrato` e `cd mcp && pnpm gerar:contrato`. Verificar com `ContratoOpenApiIT` sem a flag e com `ContratoModelosTest` no mobile passando.

## 6. Web

- [x] 6.1 Em `apiClient.ts`, ler o ProblemDetail do 401 (via `response.clone()`) antes do refresh: `title == "Conta excluída"` → `encerrarSessaoContaExcluida()` sem chamar `/auth/refresh`; `detail == "Credenciais inválidas."` (senha errada em form) → devolve sem refresh. Verificado em `apiClient.contaExcluida.test.ts` (sem refresh, corpo ainda legível, concorrência, senha errada mantém sessão, 401 comum/HTML/JSON não-objeto seguem o refresh).
- [x] 6.2 `tokenStore` ganha `encerrarSessaoContaExcluida()` (limpa o token e guarda um aviso de uma vez em `sessionStorage`); o `ProtectedRoute` já leva ao login, e o `LoginPage` mostra "Sua conta foi excluída." e consome o aviso. Verificado em `LoginPage.test.tsx` (aviso visível, some depois de lido, login comum sem aviso).
- [x] 6.3 Em `meApi.ts`, adicionar `exportarDados()` (blob + nome do `Content-Disposition`) e `excluirConta(senhaAtual)`; a exclusão trata 401 "Credenciais inválidas" como erro de formulário, sem refresh. Verificar em `meApi.test.ts` com a matriz padrão (2xx, 400, 401, 404, 409, 500/timeout, HTML do proxy).
- [x] 6.4 Em `PerfilPage.tsx`, adicionar a seção "Seus dados" (Exportar) e a área de perigo com diálogo de exclusão (texto de irreversibilidade, link para exportar, senha, confirmação `EXCLUIR`) (design D6). Verificar em `PerfilPage.test.tsx` cada cenário de `ui-conta-titular`: download, clique duplo, falha no export, botão bloqueado sem `EXCLUIR`, cancelar, sucesso → login com aviso, senha errada no campo, 500 mantém sessão. Conferir no navegador (preview) desktop e 375 px.

## 7. Android

- [x] 7.1 Em `ApiHttp.kt`, ler o ProblemDetail do 401 antes do refresh: "Conta excluída" → apaga a sessão local, emite `contaExcluida` e lança `ApiErro.ContaExcluida` (também se aparecer depois do refresh); "Credenciais inválidas." → `ApiErro.Http(401)` sem refresh. Verificado em `ApiHttpContaExcluidaTest` (sem refresh, concorrência, depois do refresh, senha errada, corpo vazio renova, DELETE com corpo, export cru, 500).
- [x] 7.2 Em `RotinaApi`, adicionar `exportarDados(): ByteArray` e `excluirConta(senhaAtual)`; `Autenticador.contaExcluida` (flow com padrão vazio) e `SessaoViewModel.encerrarPorContaExcluida()` levam ao login com "Sua conta foi excluída." e apagam os dados locais. Verificado em `SessaoViewModelTest` (outro dispositivo, sem rede, form limpo).
- [x] 7.3 Criar `SalvadorArquivo` em `commonMain` (`SemSalvadorArquivo` como padrão), injetado por `AppDependencias`, com implementação `SalvadorArquivoAndroid` (`CreateDocument("application/json")` registrado na `MainActivity`). `PerfilViewModel` + `PerfilTela` com Exportar e Excluir conta (senha + `EXCLUIR`). Verificado em `PerfilViewModelTest` (12 cenários), `./gradlew :shared:jvmTest :shared:koverVerifyJvm :androidApp:compileDebugKotlin`.

## 8. MCP

- [x] 8.1 Em `mcp/src/api-client.ts`, tratar 401 "Conta excluída" (também depois do refresh): sem refresh, `sessionStore.clear()`, `ContaExcluidaError`; `errorResult` devolve `code: "CONTA_EXCLUIDA"`. Verificado em `conta-excluida.test.ts` (sem refresh, chamada seguinte pede login, corpo vazio/HTML renovam, depois do refresh, 500 → `ApiError`, sem token na mensagem).
- [x] 8.2 Teste de guarda: as ferramentas registradas são exatamente `MCP_TOOL_NAMES` e nenhuma fala em conta/export; o `ControleRotinaApiClient` não tem método de excluir/exportar; `api-client.ts` e `server.ts` não citam `/me/export` nem `DELETE /me`. `pnpm test` verde.

## 9. Integração e gates

- [x] 9.1 Rodar `cd backend && mvn test`, `cd frontend/frontend-web && npm run build && npm run test:coverage`, `cd mcp && pnpm test:coverage` e o comando Kover do mobile; todos acima do limite. Elevar os limites de cobertura que subirem (catraca).
- [x] 9.2 Ponta a ponta no web (preview, banco local com V29): conta de teste com tarefa; `GET /me/export` com anexo datado, a tarefa e sem senha; diálogo com senha errada mostra "Senha atual incorreta." sem deslogar; senha certa + `EXCLUIR` leva ao login com "Sua conta foi excluída."; o token do segundo login recebe 401 "Conta excluída" e a tela cai no login com o aviso; banco sem linhas da conta. O clique em "Exportar meus dados" (download no navegador) não foi exercitado; o endpoint foi conferido via `fetch`.

## Workflow follow-up

- Abrir PR referenciando a issue #75 e, após o merge, arquivar a change com `/openspec-archive-change`.
